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
import io.fabric8.kubernetes.client.utils.Serialization;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpHeaders;
import org.eclipse.jkube.kit.common.HttpURLConnectionResponse;
import org.eclipse.jkube.kit.common.KitLogger;
import org.eclipse.jkube.kit.common.util.SHAUtil;
import org.eclipse.jkube.kit.resource.helm.BadUploadException;
import org.eclipse.jkube.kit.resource.helm.HelmRepository;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.net.HttpURLConnection.HTTP_ACCEPTED;
import static java.net.HttpURLConnection.HTTP_NOT_FOUND;
import static java.net.HttpURLConnection.HTTP_OK;
import static org.eclipse.jkube.kit.common.util.IoUtil.appendQueryParam;
import static org.eclipse.jkube.kit.common.util.IoUtil.doHttpRequest;
import static org.eclipse.jkube.kit.common.util.IoUtil.getHeaderValueFromHeaders;
import static org.eclipse.jkube.kit.common.util.IoUtil.isResponseSuccessful;

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
    HttpURLConnectionResponse response = doHttpRequest(logger, "POST", uploadProcessInitiateUrl, createStandardHeadersOCI());

    int responseCode = response.getCode();
    if (responseCode != HTTP_ACCEPTED) {
      throw new IllegalStateException("Failure in initiating upload request: " + response.getError());
    } else {
      String locationHeader = parseLocationHeaderFromResponse(response.getHeaders(), ociRegistryEndpoint.getBaseUrl());
      if (StringUtils.isBlank(locationHeader)) {
        throw new IllegalStateException(String.format("No %s header found in upload initiation response", HttpHeaders.LOCATION));
      }
      return locationHeader;
    }
  }

  public String uploadOCIManifest(String chartName, String chartVersion, String chartConfigDigest, String chartTarballDigest, int chartConfigPayloadSize, long chartTarballContentSize) throws IOException, BadUploadException {
    String manifestUrl = ociRegistryEndpoint.getManifestUrl(chartName, chartVersion);
    String manifestPayload = createChartManifestPayload(chartConfigDigest, chartTarballDigest, chartConfigPayloadSize, chartTarballContentSize);
    Map<String, String> requestHeaders = createStandardHeadersOCI();
    requestHeaders.put(HttpHeaders.CONTENT_TYPE, OCI_IMAGE_MANIFEST_MEDIA_TYPE);
    requestHeaders.put(HttpHeaders.CONTENT_LENGTH, Integer.toString(manifestPayload.getBytes().length));
    requestHeaders.put(HttpHeaders.HOST, repository.getUrl());

    HttpURLConnectionResponse response = doHttpRequest(logger, "PUT", manifestUrl, requestHeaders, manifestPayload, null);

    int responseCode = response.getCode();
    if (!isResponseSuccessful(responseCode)) {
      handleFailure(response);
    }
    return extractDockerContentDigestFromResponseHeaders(response);
  }

  public String uploadBlob(String uploadUrl, String blobDigest, File blobContentFile, String blobContentStr) throws IOException, BadUploadException {
    uploadUrl = appendQueryParam(uploadUrl, "digest", String.format("sha256:%s", blobDigest));
    Map<String, String> requestHeaders = createStandardHeadersOCI();
    requestHeaders.put(HttpHeaders.CONTENT_TYPE, "application/octet-stream");
    HttpURLConnectionResponse response = doHttpRequest(logger, "PUT", uploadUrl, requestHeaders, blobContentStr, blobContentFile);

    int responseCode = response.getCode();
    if (!isResponseSuccessful(responseCode)) {
      handleFailure(response);
    }
    return extractDockerContentDigestFromResponseHeaders(response);
  }

  public boolean isLayerUploadedAlready(String chartName, String digest) throws IOException {
    String blobExistenceCheckUrl = ociRegistryEndpoint.getBlobUrl(chartName, digest);
    HttpURLConnectionResponse response = doHttpRequest(logger, "HEAD", blobExistenceCheckUrl, createStandardHeadersOCI());

    int responseCode = response.getCode();
    if (responseCode == HTTP_NOT_FOUND) {
      return false;
    }
    return responseCode == HTTP_OK;
  }

  private void handleFailure(HttpURLConnectionResponse response) throws BadUploadException {
    int responseCode = response.getCode();
    if (responseCode == HttpURLConnection.HTTP_BAD_REQUEST) {
      throw new BadUploadException(response.getError());
    } else {
      throw new IllegalStateException("Received " + responseCode + " : " + response.getError());
    }
  }

  private String extractDockerContentDigestFromResponseHeaders(HttpURLConnectionResponse response) {
    String dockerContentDigest = getHeaderValueFromHeaders(response.getHeaders(), DOCKER_CONTENT_DIGEST);
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

  private String createChartManifestPayload(String chartConfigDigest, String chartTarballDigest, int chartConfigPayloadSize, long chartTarballContentSize) throws JsonProcessingException {
    OCIManifest manifest = createChartManifest(chartConfigDigest, chartTarballDigest, chartConfigPayloadSize, chartTarballContentSize);
    return Serialization.jsonMapper().writeValueAsString(manifest);
  }

  private OCIManifest createChartManifest(String digest, String layerDigest, int chartConfigPayloadSize, long chartTarballContentSize) {
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