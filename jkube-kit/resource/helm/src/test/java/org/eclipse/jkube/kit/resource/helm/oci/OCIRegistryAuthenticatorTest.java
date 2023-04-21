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
import org.eclipse.jkube.kit.common.util.Base64Util;
import org.eclipse.jkube.kit.common.util.IoUtil;
import org.eclipse.jkube.kit.resource.helm.HelmRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.net.HttpURLConnection.HTTP_FORBIDDEN;
import static java.net.HttpURLConnection.HTTP_OK;
import static java.net.HttpURLConnection.HTTP_UNAUTHORIZED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mockStatic;

class OCIRegistryAuthenticatorTest {
  private OCIRegistryAuthenticator ociRegistryAuthenticator;
  private KitLogger logger;
  private MockedStatic<IoUtil> ioUtilMockedStatic;

  @BeforeEach
  void setUp() {
    logger = new KitLogger.SilentLogger();
    ociRegistryAuthenticator = createNewOCIRegistryAuthenticator(logger);
    ioUtilMockedStatic = mockStatic(IoUtil.class);
  }

  @AfterEach
  void tearDown() {
    ioUtilMockedStatic.close();
  }

  @Test
  void authenticate_whenInitialCallSuccessful_thenReturnTrue() throws IOException {
    // Given
    String originalRegistryUrl = "https://r.example.com/v2/myuser/blobs/uploads";
    givenInitialCallHasResponseCode(HTTP_OK, originalRegistryUrl, Collections.emptyMap());

    // When + Then
    assertThat(ociRegistryAuthenticator.authenticate(originalRegistryUrl)).isTrue();
  }

  @Test
  void authenticate_whenUnauthenticatedResponseHasNoWwwHeader_thenThrowException() throws IOException {
    String originalRegistryUrl = "https://r.example.com/v2/myuser/blobs/uploads";
    givenInitialCallHasResponseCode(HTTP_UNAUTHORIZED, originalRegistryUrl, Collections.emptyMap());

    // When
    assertThatIllegalStateException()
        .isThrownBy(() -> ociRegistryAuthenticator.authenticate(originalRegistryUrl))
        .withMessage("Got 401 but no WWW-Authenticate found in response headers ");
  }

  @Test
  void authenticate_whenUnauthenticatedAndFollowUpAuthCallFails_thenReturnFalse() throws IOException {
    String originalRegistryUrl = "https://r.example.com/v2/myuser/blobs/uploads";
    String authUrl = "https://r.example.com/token";
    String service = "r.example.com";
    String wwwHeader = createWwwHeader(authUrl, service);
    Map<String, List<String>> unAuthorizedResponseHeaders = Collections.singletonMap(HttpHeaders.WWW_AUTHENTICATE, Collections.singletonList(wwwHeader));
    Map<String, String> authenticationRequestHeader = Collections.singletonMap(HttpHeaders.AUTHORIZATION, String.format("Basic %s", Base64Util.encodeToString("myuser:secret")));
    Map<String, String> wwwHeadersMap = createWwwHeadersAsMap(authUrl, service);
    String authUrlWithQueryParams = String.format("%s?service=%s&scope=%s", authUrl, "r.example.com", wwwHeadersMap.get("scope") + ",push");
    givenInitialCallHasResponseCode(HTTP_UNAUTHORIZED, originalRegistryUrl, unAuthorizedResponseHeaders);
    givenUnauthorizedRequestHasWwwAuthenticateHeaders(authUrl, unAuthorizedResponseHeaders, wwwHeader, service);
    ioUtilMockedStatic.when(() -> IoUtil.doHttpRequest(logger, "GET", authUrlWithQueryParams, authenticationRequestHeader))
        .thenReturn(createNewHttpResponse(HTTP_FORBIDDEN, null, Collections.emptyMap(), "DENIED"));

    // When
    boolean authenticated = ociRegistryAuthenticator.authenticate(originalRegistryUrl);

    // Then
    assertThat(authenticated).isFalse();
    assertThat(ociRegistryAuthenticator.getOAuthToken()).isBlank();
  }

  @Test
  void authenticate_whenUnauthenticated_thenShouldAuthenticateWithGetAndFetchAccessToken() throws IOException {
    // Given
    String originalRegistryUrl = "https://r.example.com/v2/myuser/blobs/uploads";
    String authUrl = "https://r.example.com/token";
    String service = "r.example.com";
    String wwwHeader = createWwwHeader(authUrl, service);
    Map<String, List<String>> unAuthorizedResponseHeaders = Collections.singletonMap(HttpHeaders.WWW_AUTHENTICATE, Collections.singletonList(wwwHeader));
    Map<String, String> authenticationRequestHeader = Collections.singletonMap(HttpHeaders.AUTHORIZATION, String.format("Basic %s", Base64Util.encodeToString("myuser:secret")));
    Map<String, String> wwwHeadersMap = createWwwHeadersAsMap(authUrl, service);
    String authUrlWithQueryParams = String.format("%s?service=%s&scope=%s", authUrl, "r.example.com", wwwHeadersMap.get("scope") + ",push");
    givenInitialCallHasResponseCode(HTTP_UNAUTHORIZED, originalRegistryUrl, unAuthorizedResponseHeaders);
    givenUnauthorizedRequestHasWwwAuthenticateHeaders(authUrl, unAuthorizedResponseHeaders, wwwHeader, service);
    givenAuthHttpCallHasSuccessfulResponse(authUrlWithQueryParams, authenticationRequestHeader);

    // When
    boolean authenticated = ociRegistryAuthenticator.authenticate(originalRegistryUrl);

    // Then
    assertThat(authenticated).isTrue();
    assertThat(ociRegistryAuthenticator.getOAuthToken()).isEqualTo("mytoken");
  }

  @Test
  void authenticate_withDockerAuthUnauthenticated_thenShouldAuthenticateWithPostAndFetchAccessToken() throws IOException {
    // Given
    String originalRegistryUrl = "https://registry-1.docker.io/v2/myuser/blobs/uploads";
    String authUrl = "https://auth.docker.io/token";
    String service = "registry.docker.io";
    String wwwHeader = createWwwHeader(authUrl, service);
    Map<String, List<String>> responseHeaders = Collections.singletonMap(HttpHeaders.WWW_AUTHENTICATE, Collections.singletonList(wwwHeader));
    String postRequestPayload = "{\"grant_type\"=\"password\"}";
    givenInitialCallHasResponseCode(HTTP_UNAUTHORIZED, originalRegistryUrl, responseHeaders);
    givenUnauthorizedRequestHasWwwAuthenticateHeaders(authUrl, responseHeaders, wwwHeader, service);
    givenAuthHttpCallHasSuccessfulResponse(authUrl, Collections.emptyMap());
    ioUtilMockedStatic.when(() -> IoUtil.createFormDataStringFromMap(anyMap()))
        .thenReturn(postRequestPayload);

    // When
    boolean authenticated = ociRegistryAuthenticator.authenticate(originalRegistryUrl);

    // Then
    assertThat(authenticated).isTrue();
    assertThat(ociRegistryAuthenticator.getOAuthToken()).isEqualTo("mytoken");
  }

  private void givenInitialCallHasResponseCode(int responseCode, String originalRegistryUrl, Map<String, List<String>> responseHeaders) {
    ioUtilMockedStatic.when(() -> IoUtil.doHttpRequest(logger, "HEAD", originalRegistryUrl, Collections.emptyMap()))
        .thenReturn(createNewHttpResponse(responseCode, null, responseHeaders, null));
  }

  private void givenUnauthorizedRequestHasWwwAuthenticateHeaders(String authUrl, Map<String, List<String>> responseHeaders, String wwwHeader, String service) {
    ioUtilMockedStatic.when(() -> IoUtil.getHeaderValueFromHeaders(responseHeaders, HttpHeaders.WWW_AUTHENTICATE))
        .thenReturn(wwwHeader);
    ioUtilMockedStatic.when((() -> IoUtil.parseWwwAuthenticateHeaderToMap(wwwHeader)))
        .thenReturn(createWwwHeadersAsMap(authUrl, service));
  }

  private void givenAuthHttpCallHasSuccessfulResponse(String url, Map<String, String> requestHeaders) {
    if (url.equals("https://auth.docker.io/token")) {
      ioUtilMockedStatic.when(() -> IoUtil.doHttpRequest(eq(logger), eq("POST"), eq(url), anyMap(), anyString(), isNull()))
          .thenReturn(createNewHttpResponse(HTTP_OK, "{\"access_token\":\"mytoken\"}", Collections.emptyMap(), null));
    } else {
      ioUtilMockedStatic.when(() -> IoUtil.doHttpRequest(logger, "GET", url, requestHeaders))
          .thenReturn(createNewHttpResponse(HTTP_OK, "{\"token\":\"mytoken\"}", Collections.emptyMap(), null));
    }
  }

  private HttpURLConnectionResponse createNewHttpResponse(int responseCode, String responseBody, Map<String, List<String>> responseHeaders, String error) {
    HttpURLConnectionResponse.HttpURLConnectionResponseBuilder responseBuilder = HttpURLConnectionResponse.builder();
    responseBuilder.code(responseCode);
    if (StringUtils.isNotBlank(responseBody)) {
      responseBuilder.body(responseBody);
    }
    if (responseHeaders != null) {
      responseBuilder.headers(responseHeaders);
    }
    if (StringUtils.isNotBlank(error)) {
      responseBuilder.error(error);
    }

    return responseBuilder.build();
  }

  private String createWwwHeader(String authUrl, String service) {
    return String.format("Bearer realm=\"%s\",service=\"%s\",scope=\"repository:%s/%s:pull\"", authUrl, service, "myuser", "test-chart");
  }

  private OCIRegistryAuthenticator createNewOCIRegistryAuthenticator(KitLogger logger) {
    return new OCIRegistryAuthenticator(HelmRepository.builder()
        .username("myuser")
        .password("secret")
        .build(), logger);
  }

  private Map<String, String> createWwwHeadersAsMap(String authUrl, String service) {
    Map<String, String> wwwHeadersMap = new HashMap<>();
    wwwHeadersMap.put("Bearer realm", authUrl);
    wwwHeadersMap.put("scope", "repository:myuser/test-chart" + ":pull");
    wwwHeadersMap.put("service", service);
    return wwwHeadersMap;
  }
}
