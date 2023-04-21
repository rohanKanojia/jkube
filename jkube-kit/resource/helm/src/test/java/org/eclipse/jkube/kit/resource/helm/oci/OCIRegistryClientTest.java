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

import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpHeaders;
import org.eclipse.jkube.kit.common.HttpURLConnectionResponse;
import org.eclipse.jkube.kit.common.KitLogger;
import org.eclipse.jkube.kit.common.util.IoUtil;
import org.eclipse.jkube.kit.resource.helm.BadUploadException;
import org.eclipse.jkube.kit.resource.helm.HelmRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.net.HttpURLConnection.HTTP_ACCEPTED;
import static java.net.HttpURLConnection.HTTP_BAD_REQUEST;
import static java.net.HttpURLConnection.HTTP_CREATED;
import static java.net.HttpURLConnection.HTTP_NOT_FOUND;
import static java.net.HttpURLConnection.HTTP_OK;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class OCIRegistryClientTest {
  private KitLogger logger;
  private OCIRegistryAuthenticator ociRegistryAuthenticator;
  private OCIRegistryClient oci;
  private String chartName;
  private String chartVersion;
  private String chartTarballBlobDigest;
  private String chartConfigBlobDigest;
  private int chartConfigPayloadSizeInBytes;
  private int chartTarballContentSizeInBytes;
  private File chartFile;
  private MockedStatic<IoUtil> ioUtilMockedStatic;
  @TempDir
  File temporaryFolder;

  @BeforeEach
  void setUp() throws IOException {
    logger = new KitLogger.SilentLogger();
    HelmRepository helmRepository = HelmRepository.builder()
        .url("https://r.example.com/myuser")
        .build();
    ociRegistryAuthenticator = mock(OCIRegistryAuthenticator.class);
    when(ociRegistryAuthenticator.getOAuthToken()).thenReturn("sometoken");
    chartName = "test-chart";
    chartVersion = "0.0.1";
    chartConfigBlobDigest = "f2ab3e153f678e5f01062717a203f4ca47a556159bcbb1e8a3ec5d84b5dd7aef";
    chartTarballBlobDigest = "98c4987b6502c7eb8e29a8844e0e1f1d19a8925594f8271ae70f9a51412e737a";
    chartConfigPayloadSizeInBytes = 10;
    chartTarballContentSizeInBytes = 100;
    chartFile = new File(temporaryFolder, "test-chart-0.0.1.tar.gz");
    assertThat(chartFile.createNewFile()).isTrue();
    ioUtilMockedStatic = mockStatic(IoUtil.class);
    ioUtilMockedStatic.when(() -> IoUtil.getHeaderValueFromHeaders(anyMap(), anyString())).thenCallRealMethod();
    ioUtilMockedStatic.when(() -> IoUtil.appendQueryParam(anyString(), anyString(), anyString())).thenCallRealMethod();
    ioUtilMockedStatic.when(() -> IoUtil.isResponseSuccessful(anyInt())).thenCallRealMethod();
    oci = new OCIRegistryClient(logger, helmRepository, ociRegistryAuthenticator);
  }

  @AfterEach
  void tearDown() {
    ioUtilMockedStatic.close();
  }

  @Test
  void getBaseUrl_whenInvoked_shouldReturnRegistryUrl() throws MalformedURLException {
    assertThat(oci.getBaseUrl()).isEqualTo("https://r.example.com");
  }

  @Test
  void verifyIfAuthorizedToPushChart_whenUnAuthorized_thenThrowException() throws IOException {
    // Given
    when(ociRegistryAuthenticator.authenticate(anyString())).thenReturn(false);

    // When + Then
    assertThatIllegalStateException()
        .isThrownBy(() -> oci.verifyIfAuthorizedToPushChart(chartName, chartFile))
        .withMessage("Failure in authentication against Registry");
  }

  @Test
  void initiateUploadProcess_whenRegistryResponseSuccessfulAndContainsLocation_thenReturnUploadUrl() throws IOException {
    // Given
    Map<String, List<String>> uploadResponseHeaders = new HashMap<>();
    String responseLocationHeader = "https://r.example.com/v2/myuser/test-chart/blobs/uploads/17f1053c-fcd7-47a7-a34b-bbf23bbdf906?_state=uploadstate";
    uploadResponseHeaders.put(HttpHeaders.LOCATION, Collections.singletonList(responseLocationHeader));
    ioUtilMockedStatic.when(() -> IoUtil.doHttpRequest(eq(logger), eq("POST"), anyString(), anyMap()))
        .thenReturn(createHttpResponseWithCode(HTTP_ACCEPTED, uploadResponseHeaders, null));

    // When
    String uploadUrl = oci.initiateUploadProcess(chartName);

    // Then
    assertThat(uploadUrl).isEqualTo(responseLocationHeader);
  }

  @Test
  void initiateUploadProcess_whenRegistryResponseSuccessfulButLocationHeaderContainsPathOnly_thenReturnUploadUrl() throws IOException {
    // Given
    Map<String, List<String>> uploadResponseHeaders = new HashMap<>();
    String responseLocationHeader = "/v2/myuser/test-chart/blobs/uploads/17f1053c-fcd7-47a7-a34b-bbf23bbdf906?_state=uploadstate";
    uploadResponseHeaders.put(HttpHeaders.LOCATION, Collections.singletonList(responseLocationHeader));
    ioUtilMockedStatic.when(() -> IoUtil.doHttpRequest(eq(logger), eq("POST"), anyString(), anyMap()))
        .thenReturn(createHttpResponseWithCode(HTTP_ACCEPTED, uploadResponseHeaders, null));

    // When
    String uploadUrl = oci.initiateUploadProcess(chartName);

    // Then
    assertThat(uploadUrl).isEqualTo("https://r.example.com" + responseLocationHeader);
  }

  @Test
  void initiateUploadProcess_whenRegistryResponseSuccessfulButNoHeader_thenThrowException() {
    // Given
    ioUtilMockedStatic.when(() -> IoUtil.doHttpRequest(eq(logger), eq("POST"), anyString(), anyMap()))
        .thenReturn(createHttpResponseWithCode(HTTP_ACCEPTED, Collections.emptyMap(), null));
    ioUtilMockedStatic.when(() -> IoUtil.getHeaderValueFromHeaders(anyMap(), eq(HttpHeaders.LOCATION)))
        .thenReturn(null);

    // When + Then
    assertThatIllegalStateException()
        .isThrownBy(() -> oci.initiateUploadProcess(chartName))
        .withMessage("No Location header found in upload initiation response");
  }

  @Test
  void initiateUploadProcess_whenRegistryResponseFailure_thenThrowException() throws IOException {
    // Given
    ioUtilMockedStatic.when(() -> IoUtil.doHttpRequest(eq(logger), eq("POST"), anyString(), anyMap()))
        .thenReturn(createHttpResponseWithCode(HTTP_NOT_FOUND, Collections.emptyMap(), "Not Found"));

    // When + Then
    assertThatIllegalStateException()
        .isThrownBy(() -> oci.initiateUploadProcess(chartName))
        .withMessage("Failure in initiating upload request: Not Found");
  }

  @Test
  void uploadOCIManifest_whenManifestSuccessfullyPushed_thenReturnDockerContentDigest() throws BadUploadException, IOException {
    // Given
    String responseDockerContentDigestHeader = "sha256:createdmanifestdigest";
    ioUtilMockedStatic.when(() -> IoUtil.doHttpRequest(eq(logger), eq("PUT"), eq("https://r.example.com/v2/myuser/test-chart/manifests/0.0.1"), anyMap(), anyString(), isNull()))
        .thenReturn(createHttpResponseWithCode(HTTP_CREATED, Collections.singletonMap("docker-content-digest", Collections.singletonList(responseDockerContentDigestHeader)), null));

    // When
    String dockerContentDigest = oci.uploadOCIManifest(chartName, chartVersion, chartConfigBlobDigest, chartTarballBlobDigest, chartConfigPayloadSizeInBytes, chartTarballContentSizeInBytes);

    // Then
    assertThat(dockerContentDigest).isEqualTo(responseDockerContentDigestHeader);
  }

  @Test
  void uploadOCIManifest_whenRegistryRejectedManifest_thenThrowException() throws IOException {
    // Given
    ioUtilMockedStatic.when(() -> IoUtil.doHttpRequest(eq(logger), eq("PUT"), eq("https://r.example.com/v2/myuser/test-chart/manifests/0.0.1"), anyMap(), anyString(), isNull()))
        .thenReturn(createHttpResponseWithCode(HTTP_BAD_REQUEST, null, "invalid manifest"));

    // When + Then
    assertThatExceptionOfType(BadUploadException.class)
        .isThrownBy(() -> oci.uploadOCIManifest(chartName, chartVersion, chartConfigBlobDigest, chartTarballBlobDigest, chartConfigPayloadSizeInBytes, chartTarballContentSizeInBytes))
        .withMessage("invalid manifest");
  }

  @Test
  void uploadOCIManifest_whenManifestSuccessfullyPushedButNoDockerContentDigest_thenThrowException() throws BadUploadException, IOException {
    // Given
    ioUtilMockedStatic.when(() -> IoUtil.doHttpRequest(eq(logger), eq("PUT"), eq("https://r.example.com/v2/myuser/test-chart/manifests/0.0.1"), anyMap(), anyString(), isNull()))
        .thenReturn(createHttpResponseWithCode(HTTP_CREATED, Collections.emptyMap(), null));

    // When
    assertThatIllegalStateException()
        .isThrownBy(() -> oci.uploadOCIManifest(chartName, chartVersion, chartConfigBlobDigest, chartTarballBlobDigest, chartConfigPayloadSizeInBytes, chartTarballContentSizeInBytes))
        .withMessage("No Docker-Content-Digest header found in upload response");
  }

  @Test
  void uploadBlob_whenBlobSuccessfullyPushedToRegistry_thenReturnDockerContentDigest() throws BadUploadException, IOException {
    // Given
    String blobUploadUrl = "https://r.example.com/v2/myuser/test-chart/blobs/uploads/17f1053c-fcd7-47a7-a34b-bbf23bbdf906?_state=XZnxHKS";
    Map<String, List<String>> responseHeaders = new HashMap<>();
    responseHeaders.put("Docker-Content-Digest", Collections.singletonList("sha256:016b77128b6bdf63ce4000e38fc36dcb15dfd6feea2d244a2c797a2d4f75a2de"));
    ioUtilMockedStatic.when(() -> IoUtil.doHttpRequest(eq(logger), eq("PUT"), anyString(), anyMap(), isNull(), any(File.class)))
        .thenReturn(createHttpResponseWithCode(HTTP_CREATED, responseHeaders, null));

    // When
    String dockerContentDigest = oci.uploadBlob(blobUploadUrl, chartTarballBlobDigest, chartFile, null);

    // Then
    assertThat(dockerContentDigest)
        .isEqualTo("sha256:016b77128b6bdf63ce4000e38fc36dcb15dfd6feea2d244a2c797a2d4f75a2de");
  }

  @Test
  void uploadBlob_whenBlobRejectedByRegistry_thenThrowException() throws BadUploadException, IOException {
    // Given
    String blobUploadUrl = "https://r.example.com/v2/myuser/test-chart/blobs/uploads/17f1053c-fcd7-47a7-a34b-bbf23bbdf906?_state=XZnxHKS";
    ioUtilMockedStatic.when(() -> IoUtil.doHttpRequest(eq(logger), eq("PUT"), anyString(), anyMap(), isNull(), any(File.class)))
        .thenReturn(createHttpResponseWithCode(HTTP_BAD_REQUEST, Collections.emptyMap(), "invalid data"));

    // When + Then
    assertThatExceptionOfType(BadUploadException.class)
        .isThrownBy(() -> oci.uploadBlob(blobUploadUrl, chartTarballBlobDigest, chartFile, null))
        .withMessage("invalid data");
  }

  @Test
  void uploadBlob_whenBlobSuccessfullyPushedToRegistryButNoDockerContentDigest_thenThrowException() throws BadUploadException, IOException {
    // Given
    String blobUploadUrl = "https://r.example.com/v2/myuser/test-chart/blobs/uploads/17f1053c-fcd7-47a7-a34b-bbf23bbdf906?_state=XZnxHKS";
    ioUtilMockedStatic.when(() -> IoUtil.doHttpRequest(eq(logger), eq("PUT"), anyString(), anyMap(), isNull(), any(File.class)))
        .thenReturn(createHttpResponseWithCode(HTTP_CREATED, Collections.emptyMap(), null));

    // When + Then
    assertThatIllegalStateException()
        .isThrownBy(() -> oci.uploadBlob(blobUploadUrl, chartTarballBlobDigest, chartFile, null))
        .withMessage("No Docker-Content-Digest header found in upload response");
  }

  @Test
  void isLayerUploadedAlready_whenRegistryReturns200_thenReturnTrue() throws IOException {
    // Given
    String blobUrl = "https://r.example.com/v2/myuser/test-chart/blobs/sha256:" + chartConfigBlobDigest;
    ioUtilMockedStatic.when(() -> IoUtil.doHttpRequest(eq(logger), eq("HEAD"), eq(blobUrl), anyMap()))
        .thenReturn(createHttpResponseWithCode(HTTP_OK, Collections.emptyMap(), null));

    // When
    boolean result = oci.isLayerUploadedAlready(chartName, chartConfigBlobDigest);

    // Then
    assertThat(result).isTrue();
  }

  @Test
  void isLayerUploadedAlready_whenRegistryReturns404_thenReturnFalse() throws IOException {
    // Given
    String blobUrl = "https://r.example.com/v2/myuser/test-chart/blobs/sha256:" + chartConfigBlobDigest;
    ioUtilMockedStatic.when(() -> IoUtil.doHttpRequest(eq(logger), eq("HEAD"), eq(blobUrl), anyMap()))
        .thenReturn(createHttpResponseWithCode(HTTP_NOT_FOUND, Collections.emptyMap(), null));

    // When
    boolean result = oci.isLayerUploadedAlready(chartName, chartConfigBlobDigest);

    // Then
    assertThat(result).isFalse();
  }

  private HttpURLConnectionResponse createHttpResponseWithCode(int responseCode, Map<String, List<String>> headers, String error) {
    HttpURLConnectionResponse.HttpURLConnectionResponseBuilder responseBuilder = HttpURLConnectionResponse.builder();
    responseBuilder.code(responseCode);
    if (headers != null) {
      responseBuilder.headers(headers);
    }
    if (StringUtils.isNotBlank(error)) {
      responseBuilder.error(error);
    }
    return responseBuilder.build();
  }
}
