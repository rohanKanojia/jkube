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
package org.eclipse.jkube.kit.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Collections;

import static java.net.HttpURLConnection.HTTP_NOT_FOUND;
import static java.net.HttpURLConnection.HTTP_OK;
import static org.assertj.core.api.Assertions.assertThat;

class HttpURLConnectionResponseTest {
  @Test
  void rawDeserialization() throws IOException {
    // Given
    final ObjectMapper mapper = new ObjectMapper();
    // When
    final HttpURLConnectionResponse result = mapper.readValue(
        "{\"code\":404,\"message\": \"Not Found\",\"error\": \"Not Found\", \"headers\":{\"h1\": [\"v1\"]}}",
        HttpURLConnectionResponse.class);
    // Then
    assertHttpURLConnectionResponse(result);
  }

  @Test
  void builder() {
    // Given
    HttpURLConnectionResponse.HttpURLConnectionResponseBuilder responseBuilder = HttpURLConnectionResponse.builder()
        .code(HTTP_NOT_FOUND)
        .message("Not Found")
        .error("Not Found")
        .headers(Collections.singletonMap("h1", Collections.singletonList("v1")));

    // When
    HttpURLConnectionResponse response = responseBuilder.build();

    // Then
    assertHttpURLConnectionResponse(response);
  }

  private void assertHttpURLConnectionResponse(HttpURLConnectionResponse response) {
    assertThat(response)
        .hasFieldOrPropertyWithValue("code", HTTP_NOT_FOUND)
        .hasFieldOrPropertyWithValue("message", "Not Found")
        .hasFieldOrPropertyWithValue("error", "Not Found")
        .hasFieldOrPropertyWithValue("headers", Collections.singletonMap("h1", Collections.singletonList("v1")));
  }

  @Test
  void equalsAndHashCodeShouldMatch() {
    // Given
    HttpURLConnectionResponse  r1 = HttpURLConnectionResponse.builder().code(HTTP_OK).build();
    HttpURLConnectionResponse r2 = HttpURLConnectionResponse.builder().code(HTTP_OK).build();
    // When + Then
    assertThat(r1)
        .isEqualTo(r2)
        .hasSameHashCodeAs(r2);
  }
}
