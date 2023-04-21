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
import org.eclipse.jkube.kit.common.util.Base64Util;
import org.eclipse.jkube.kit.resource.helm.HelmRepository;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.eclipse.jkube.kit.common.util.IoUtil.createFormDataStringFromMap;
import static org.eclipse.jkube.kit.common.util.IoUtil.doHttpRequest;
import static org.eclipse.jkube.kit.common.util.IoUtil.getHeaderValueFromHeaders;
import static org.eclipse.jkube.kit.common.util.IoUtil.parseWwwAuthenticateHeaderToMap;

public class OCIRegistryAuthenticator {
  private static final String DOCKER_AUTH_URL = "https://auth.docker.io/token";
  private static final String TOKEN_KEY = "token";
  private static final String ACCESS_TOKEN_KEY = "access_token";

  private String oAuthToken;
  private final HelmRepository repository;
  private final KitLogger logger;

  public OCIRegistryAuthenticator(HelmRepository repository, KitLogger logger) {
    this.repository = repository;
    this.logger = logger;
    this.oAuthToken = StringUtils.EMPTY;
  }

  public boolean authenticate(String url) throws IOException {
    HttpURLConnectionResponse response = doHttpRequest(logger, "HEAD", url, Collections.emptyMap());
    int responseCode = response.getCode();
    if (responseCode == HttpURLConnection.HTTP_UNAUTHORIZED) {
      String wwwAuthenticateHeaderField = getHeaderValueFromHeaders(response.getHeaders(), HttpHeaders.WWW_AUTHENTICATE);
      if (StringUtils.isBlank(wwwAuthenticateHeaderField)) {
        throw new IllegalStateException("Got 401 but no " + HttpHeaders.WWW_AUTHENTICATE + " found in response headers ");
      }

      return doAuthentication(wwwAuthenticateHeaderField);
    }
    return true;
  }

  public String getOAuthToken() {
    return oAuthToken;
  }

  private boolean doAuthentication(String wwwAuthenticateHeaderField) throws IOException {
    Map<String, String> result = parseWwwAuthenticateHeaderToMap(wwwAuthenticateHeaderField);
    String authenticationUrl = result.get("Bearer realm");
    String scope = result.get("scope");
    if (!scope.contains("push")) {
      scope += ",push";
    }
    String service = result.get("service");

    if (authenticationUrl.equals(DOCKER_AUTH_URL)) {
      return submitPostRequest(authenticationUrl, scope, service);
    } else {
      return submitGetRequest(authenticationUrl, scope, service);
    }
  }

  private boolean submitGetRequest(String url, String scope, String service) throws IOException {
    String authUrlWithQueryParams = String.format("%s?service=%s&scope=%s", url, service, scope);
    Map<String, String> requestHeaders = new HashMap<>();
    requestHeaders.put(HttpHeaders.AUTHORIZATION, String.format("Basic %s", Base64Util.encodeToString(repository.getUsername() + ":" + repository.getPassword())));
    HttpURLConnectionResponse response = doHttpRequest(logger, "GET", authUrlWithQueryParams, requestHeaders);

    int responseCode = response.getCode();
    if (responseCode == HttpURLConnection.HTTP_OK) {
      return parseAccessTokenFromResponse(response.getBody());
    }
    return false;
  }

  private boolean submitPostRequest(String url, String scope, String service) throws IOException {
    String postDataString = createPostFormDataString(scope, service);
    Map<String, String> requestHeaders = new HashMap<>();
    requestHeaders.put(HttpHeaders.CONTENT_TYPE, "application/x-www-form-urlencoded");
    requestHeaders.put(HttpHeaders.CONTENT_LENGTH, Integer.toString(postDataString.getBytes().length));

    HttpURLConnectionResponse response = doHttpRequest(logger, "POST", url, requestHeaders, postDataString, null);

    int responseCode = response.getCode();
    if (responseCode == HttpURLConnection.HTTP_OK) {
      return parseAccessTokenFromResponse(response.getBody());
    } else {
      logger.error(response.getError());
    }
    return false;
  }

  private boolean parseAccessTokenFromResponse(String responseBody) throws JsonProcessingException {
    Map<String, Object> responseBodyObj = Serialization.jsonMapper().readValue(responseBody, Map.class);
    String tokenFound = null;
    if (responseBodyObj.containsKey(TOKEN_KEY)) {
      tokenFound = (String) responseBodyObj.get(TOKEN_KEY);
    }
    if (responseBodyObj.containsKey(ACCESS_TOKEN_KEY)) {
      tokenFound = (String) responseBodyObj.get(ACCESS_TOKEN_KEY);
    }

    if (StringUtils.isNotBlank(tokenFound)) {
      this.oAuthToken = tokenFound;
      return true;
    }
    return false;
  }

  private String createPostFormDataString(String scope, String service) throws UnsupportedEncodingException {
    Map<String, String> postFormData = createPostFormData(scope, service);

    return createFormDataStringFromMap(postFormData);
  }

  private Map<String, String> createPostFormData(String scope, String service) {
    Map<String, String> postFormData = new HashMap<>();
    postFormData.put("grant_type", "password");
    postFormData.put("refresh_token", repository.getPassword());
    postFormData.put("service", service);
    postFormData.put("scope", scope);
    postFormData.put("client_id", "EclipseJKube");
    postFormData.put("username", repository.getUsername());
    postFormData.put("password", repository.getPassword());

    return postFormData;
  }
}