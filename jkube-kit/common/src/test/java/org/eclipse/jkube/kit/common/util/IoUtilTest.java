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
package org.eclipse.jkube.kit.common.util;

import io.fabric8.kubernetes.client.http.HttpResponse;
import org.eclipse.jkube.kit.common.KitLogger;
import org.eclipse.jkube.kit.common.TestHttpStaticServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.ServerSocket;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.net.HttpURLConnection.HTTP_NOT_FOUND;
import static java.net.HttpURLConnection.HTTP_OK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IoUtilTest {
    private KitLogger logger;

    @BeforeEach
    void setUp() {
        logger = new KitLogger.SilentLogger();
    }


    @Test
    void findOpenPort() throws IOException {
        int port = IoUtil.getFreeRandomPort();
        try (ServerSocket ss = new ServerSocket(port)) {
            assertThat(ss).isNotNull();
        }
    }

    @Test
    void findOpenPortWhenPortsAreBusy() throws IOException {
        int port = IoUtil.getFreeRandomPort(49152, 60000, 100);
        try (ServerSocket ss = new ServerSocket(port)) {
            assertThat(ss).isNotNull();
        }
        int port2 = IoUtil.getFreeRandomPort(port, 65535, 100);
        try (ServerSocket ss = new ServerSocket(port2)) {
            assertThat(ss).isNotNull();
        }
        assertThat(port2 > port).isTrue();
        assertThat(port2).isGreaterThan(port);
    }

    @Test
    void findOpenPortWithSmallAttemptsCount() throws IOException {
        int port = IoUtil.getFreeRandomPort(30000, 60000, 30);
        try (ServerSocket ss = new ServerSocket(port)) {
            assertThat(ss).isNotNull();
        }
    }

    @Test
    void findOpenPortWithLargeAttemptsCount() throws IOException {
        int port = IoUtil.getFreeRandomPort(30000, 60000, 1000);
        try (ServerSocket ss = new ServerSocket(port)) {
            assertThat(ss).isNotNull();
        }
    }

    @Test
    void invokeExceptionWhenCouldntFindPort() throws IOException {

        // find an open port to occupy
        int foundPort = IoUtil.getFreeRandomPort(30000, 65000, 1000);

        // use port
        try (ServerSocket ignored = new ServerSocket(foundPort)) {
            String expectedMessage = "Cannot find a free random port in the range [" + foundPort + ", " + foundPort + "] after 3 attempts";

            // try to use the used port
            assertThatThrownBy(() -> IoUtil.getFreeRandomPort(foundPort, foundPort, 3))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage(expectedMessage);
        }

    }

    @Test
    void testSanitizeFileName() {
        //noinspection ConstantConditions
        assertThat(IoUtil.sanitizeFileName(null)).isNull();
        assertThat(IoUtil.sanitizeFileName("Hello/&%World")).isEqualTo("Hello-World");
        assertThat(IoUtil.sanitizeFileName(" _H-.-e-.-l-l--.//o()")).isEqualTo("-H-e-l-l-o-");
        assertThat(IoUtil.sanitizeFileName("s2i-env-docker.io/fabric8/java:latest")).isEqualTo("s2i-env-docker-io-fabric8-java-latest");
    }

    @Test
    void appendQueryParam_whenUrlWithNoQueryParamProvided_thenAddQueryParam() throws UnsupportedEncodingException {
        assertThat(IoUtil.appendQueryParam("https://r.example.com", "foo", "bar"))
            .isEqualTo("https://r.example.com?foo=bar");
    }

    @Test
    void appendQueryParam_whenUrlWithQueryParamProvided_thenAddQueryParam() throws UnsupportedEncodingException {
        assertThat(IoUtil.appendQueryParam("https://r.example.com?foo=bar", "key1", "value1"))
            .isEqualTo("https://r.example.com?foo=bar&key1=value1");
    }

    @Test
    void getHeaderValueFromHeaders_whenHeaderValueProvided_thenGetHeaderValue() {
        // Given
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("WWW-Authenticate", Collections.singletonList("https://auth.example.com/token"));

        // When
        String value = IoUtil.getHeaderValueFromHeaders(headers, "WWW-Authenticate");

        // Then
        assertThat(value).isEqualTo("https://auth.example.com/token");
    }

    @Test
    void getHeaderValueFromHeaders_whenHeaderValueProvidedLowerCase_thenGetHeaderValue() {
        // Given
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("www-authenticate", Collections.singletonList("https://auth.example.com/token"));

        // When
        String value = IoUtil.getHeaderValueFromHeaders(headers, "WWW-Authenticate");

        // Then
        assertThat(value).isEqualTo("https://auth.example.com/token");
    }

    @Test
    void createFormDataStringFromMap_whenDataProvidedAsMap_thenCreateFormDataPayload() throws UnsupportedEncodingException {
        // Given
        Map<String, String> formDataMap = new HashMap<>();
        formDataMap.put("grant_type", "password");
        formDataMap.put("refresh_token", "secret");
        formDataMap.put("service", "auth.example.com");
        formDataMap.put("scope", "repository:myuser/test-chart:pull");
        formDataMap.put("client_id", "EclipseJKube");
        formDataMap.put("username", "myuser");
        formDataMap.put("password", "secret");

        // When
        String formDataPayload = IoUtil.createFormDataStringFromMap(formDataMap);

        // Then
        assertThat(formDataPayload)
            .isEqualTo("refresh_token=secret&password=secret&grant_type=password&service=auth.example.com&scope=repository%3Amyuser%2Ftest-chart%3Apull&client_id=EclipseJKube&username=myuser");
    }

    @Test
    void parseWwwAuthenticateHeaderToMap_whenWwwHeaderProvided_thenParseDataIntoMap() {
        // Given
        String wwwAuthenticate = "Bearer realm=\"https://auth.example.com/token\",service=\"registry.example.com\",scope=\"repository:myuser/test-chart:pull\"";

        // When
        Map<String, String> wwwAuthenticateAsMap = IoUtil.parseWwwAuthenticateHeaderToMap(wwwAuthenticate);

        // Then
        assertThat(wwwAuthenticateAsMap)
            .hasSize(3)
            .containsEntry("Bearer realm", "https://auth.example.com/token")
            .containsEntry("service", "registry.example.com")
            .containsEntry("scope", "repository:myuser/test-chart:pull");
    }

    @Test
    void doHttpRequest_whenValidGetRequest_thenShouldReturnResponse() throws IOException {
        File remoteDirectory = new File(getClass().getResource("/remote-resources").getFile());
        try (TestHttpStaticServer http = new TestHttpStaticServer(remoteDirectory)) {
            // Given
            String serverUrl = String.format("http://localhost:%s/%s", http.getPort(), "health");
            Map<String, String> requestHeaders = Collections.singletonMap("User-Agent", "EclipseJKube");

            // When
            HttpResponse<byte[]> response = IoUtil.doHttpRequest(logger, "GET", serverUrl, requestHeaders);

            // Then
            assertThat(response)
                .isNotNull()
                .satisfies(r -> assertThat(r.code()).isEqualTo(HTTP_OK))
                .satisfies(r -> assertThat(new String(r.body())).isEqualTo("READY"))
                .satisfies(r -> assertThat(r.message()).isEqualTo("OK"));
        }
    }

    @Test
    void doHttpRequest_whenInvalidGetRequest_thenShouldReturnResponse() throws IOException {
        File remoteDirectory = new File(getClass().getResource("/remote-resources").getFile());
        try (TestHttpStaticServer http = new TestHttpStaticServer(remoteDirectory)) {
            // Given
            String serverUrl = String.format("http://localhost:%s/%s", http.getPort(), "unknown");
            Map<String, String> requestHeaders = Collections.singletonMap("User-Agent", "EclipseJKube");

            // When
            HttpResponse<byte[]> response = IoUtil.doHttpRequest(logger, "GET", serverUrl, requestHeaders);

            // Then
            assertThat(response)
                .isNotNull()
                .satisfies(r -> assertThat(r.code()).isEqualTo(HTTP_NOT_FOUND))
                .satisfies(r -> assertThat(r.message()).isEqualTo("Not Found"));
        }
    }
}
