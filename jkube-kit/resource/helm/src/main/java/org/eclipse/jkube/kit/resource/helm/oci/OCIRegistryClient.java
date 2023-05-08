/**
 * Copyright (c) 2019 Red Hat, Inc.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at:
 *
 *     https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Red Hat, Inc. - initial API and implementation
 */
package org.eclipse.jkube.kit.resource.helm.oci;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.fabric8.kubernetes.client.http.HttpResponse;
import io.fabric8.kubernetes.client.utils.Serialization;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpHeaders;
import org.eclipse.jkube.kit.common.KitLogger;
import org.eclipse.jkube.kit.common.util.SHAUtil;
import org.eclipse.jkube.kit.resource.helm.BadUploadException;
import org.eclipse.jkube.kit.resource.helm.HelmRepository;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.net.HttpURLConnection.HTTP_ACCEPTED;
import static java.net.HttpURLConnection.HTTP_NOT_FOUND;
import static java.net.HttpURLConnection.HTTP_OK;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.eclipse.jkube.kit.common.util.IoUtil.appendQueryParam;
import static org.eclipse.jkube.kit.common.util.IoUtil.doHttpRequest;
import static org.eclipse.jkube.kit.common.util.IoUtil.getHeaderValueFromHeaders;

public class OCIRegistryClient {
  private static final String DOCKER_CONTENT_DIGEST = "Docker-Content-Digest";
  private static final String USER_AGENT = "EclipseJKube";
  private static final String OCI_IMAGE_MANIFEST_MEDIA_TYPE = "application/vnd.oci.image.manifest.v1+json";
  private static final String HELM_CONFIG_MEDIA_TYPE = "application/vnd.cncf.helm.config.v1+json";
  private static final String HELM_CHART_CONTENT_MEDIA_TYPE = "application/vnd.cncf.helm.chart.content.v1.tar+gzip";
  private final KitLogger logger;
  private final HelmRepository repository;
  private final OCIRegistryEndpoint ociRegistryEndpoint;
  private final OCIRegistryAuthenticator ociRegistryAuthenticator;

  public OCIRegistryClient(KitLogger logger, HelmRepository repository) {
    this(logger, repository, new OCIRegistryAuthenticator(repository, logger));
  }

  OCIRegistryClient(KitLogger logger, HelmRepository repository, OCIRegistryAuthenticator ociRegistryAuthenticator) {
    this.logger = logger;
    this.repository = repository;
    this.ociRegistryAuthenticator = ociRegistryAuthenticator;
    this.ociRegistryEndpoint = new OCIRegistryEndpoint(repository.getUrl());
  }

  public void verifyIfAuthorizedToPushChart(String chartName, File chartTarball) throws IOException {
    String chartTarballContentDigest = SHAUtil.generateSHA256(chartTarball);
    String url = ociRegistryEndpoint.getBlobUrl(chartName, chartTarballContentDigest);
    boolean authenticated = ociRegistryAuthenticator.authenticate(url);
    if (!authenticated) {
      throw new IllegalStateException("Failure in authentication against Registry");
    }
  }

  public String getBaseUrl() throws MalformedURLException {
    return ociRegistryEndpoint.getBaseUrl();
  }

  public String initiateUploadProcess(String chartName) throws IOException {
    String uploadProcessInitiateUrl = ociRegistryEndpoint.getBlobUploadInitUrl(chartName);
    HttpResponse<byte[]> response = doHttpRequest(logger, "POST", uploadProcessInitiateUrl, createStandardHeadersOCI());

    int responseCode = response.code();
    if (responseCode != HTTP_ACCEPTED) {
      throw new IllegalStateException("Failure in initiating upload request: " + response.message());
    } else {
      String locationHeader = parseLocationHeaderFromResponse(response.headers(), ociRegistryEndpoint.getBaseUrl());
      if (StringUtils.isBlank(locationHeader)) {
        throw new IllegalStateException(String.format("No %s header found in upload initiation response", HttpHeaders.LOCATION));
      }
      return locationHeader;
    }
  }

  public String uploadOCIManifest(String chartName, String chartVersion, String chartConfigDigest, String chartTarballDigest, long chartConfigPayloadSize, long chartTarballContentSize) throws IOException, BadUploadException {
    String manifestUrl = ociRegistryEndpoint.getManifestUrl(chartName, chartVersion);
    String manifestPayload = createChartManifestPayload(chartConfigDigest, chartTarballDigest, chartConfigPayloadSize, chartTarballContentSize);
    InputStream requestBodyInputStream = new ByteArrayInputStream(manifestPayload.getBytes(StandardCharsets.UTF_8));
    Map<String, String> requestHeaders = createStandardHeadersOCI();
    requestHeaders.put(HttpHeaders.CONTENT_TYPE, OCI_IMAGE_MANIFEST_MEDIA_TYPE);
    requestHeaders.put(HttpHeaders.CONTENT_LENGTH, Integer.toString(manifestPayload.getBytes().length));
    requestHeaders.put(HttpHeaders.HOST, new URL(repository.getUrl()).getHost());

    HttpResponse<byte[]> response = doHttpRequest(logger, "PUT", manifestUrl, requestHeaders, null, requestBodyInputStream);

    if (!response.isSuccessful()) {
      handleFailure(response);
    }
    return extractDockerContentDigestFromResponseHeaders(response);
  }

  public String uploadBlob(String uploadUrl, String blobDigest, long blobSize, String blobContentStr, File blobFile) throws IOException, BadUploadException {
    uploadUrl = appendQueryParam(uploadUrl, "digest", String.format("sha256:%s", blobDigest));
    InputStream blobContentInputStream = blobFile != null ? Files.newInputStream(blobFile.toPath()) : new ByteArrayInputStream(blobContentStr.getBytes());
    Map<String, String> requestHeaders = createStandardHeadersOCI();
    requestHeaders.put(HttpHeaders.CONTENT_TYPE, "application/octet-stream");
    requestHeaders.put(HttpHeaders.CONTENT_LENGTH, Long.toString(blobSize));
    HttpResponse<byte[]> response = doHttpRequest(logger, "PUT", uploadUrl, requestHeaders, null, blobContentInputStream);

    if (!response.isSuccessful()) {
      handleFailure(response);
    }
    return extractDockerContentDigestFromResponseHeaders(response);
  }

  public boolean isLayerUploadedAlready(String chartName, String digest) throws IOException {
    String blobExistenceCheckUrl = ociRegistryEndpoint.getBlobUrl(chartName, digest);
    HttpResponse<byte[]> response = doHttpRequest(logger, "HEAD", blobExistenceCheckUrl, createStandardHeadersOCI());

    int responseCode = response.code();
    if (responseCode == HTTP_NOT_FOUND) {
      return false;
    }
    return responseCode == HTTP_OK;
  }

  private void handleFailure(HttpResponse<byte[]> response) throws BadUploadException {
    int responseCode = response.code();
    String responseBody = Optional.ofNullable(response.body()).map(String::new).orElse(EMPTY);
    if (responseCode == HttpURLConnection.HTTP_BAD_REQUEST) {
      throw new BadUploadException(response.message() + "[" + responseBody + "]");
    } else {
      throw new IllegalStateException("Received " + responseCode + " : " + response.message() + "[" + responseBody + "]");
    }
  }

  private String extractDockerContentDigestFromResponseHeaders(HttpResponse<byte[]> response) {
    String dockerContentDigest = getHeaderValueFromHeaders(response.headers(), DOCKER_CONTENT_DIGEST);
    if (StringUtils.isBlank(dockerContentDigest)) {
      throw new IllegalStateException("No " + DOCKER_CONTENT_DIGEST + " header found in upload response");
    }
    return dockerContentDigest;
  }

  private Map<String, String> createStandardHeadersOCI() {
    Map<String, String> headers = new HashMap<>();
    headers.put(HttpHeaders.USER_AGENT, USER_AGENT);
    if (StringUtils.isNotBlank(ociRegistryAuthenticator.getOAuthToken())) {
      headers.put(HttpHeaders.AUTHORIZATION, String.format("Bearer %s", ociRegistryAuthenticator.getOAuthToken()));
    }
    return headers;
  }

  private String parseLocationHeaderFromResponse(Map<String, List<String>> headerFields, String baseUrl) {
    String locationHeader = getHeaderValueFromHeaders(headerFields, HttpHeaders.LOCATION);

    // Only path is returned via GitHub Container Registry
    if (locationHeader != null && locationHeader.startsWith("/")) {
      locationHeader = baseUrl + locationHeader;
    }
    return locationHeader;
  }

  private String createChartManifestPayload(String chartConfigDigest, String chartTarballDigest, long chartConfigPayloadSize, long chartTarballContentSize) throws JsonProcessingException {
    OCIManifest manifest = createChartManifest(chartConfigDigest, chartTarballDigest, chartConfigPayloadSize, chartTarballContentSize);
    return Serialization.jsonMapper().writeValueAsString(manifest);
  }

  private OCIManifest createChartManifest(String digest, String layerDigest, long chartConfigPayloadSize, long chartTarballContentSize) {
    return OCIManifest.builder()
        .schemaVersion(2)
        .config(OCIManifestLayer.builder()
            .mediaType(HELM_CONFIG_MEDIA_TYPE)
            .digest(digest)
            .size(chartConfigPayloadSize)
            .build())
        .layer(OCIManifestLayer.builder()
            .mediaType(HELM_CHART_CONTENT_MEDIA_TYPE)
            .digest(layerDigest)
            .size(chartTarballContentSize)
            .build())
        .build();
  }
}