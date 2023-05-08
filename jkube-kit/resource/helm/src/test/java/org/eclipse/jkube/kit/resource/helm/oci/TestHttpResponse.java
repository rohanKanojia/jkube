package org.eclipse.jkube.kit.resource.helm.oci;

import io.fabric8.kubernetes.client.http.HttpRequest;
import io.fabric8.kubernetes.client.http.HttpResponse;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public class TestHttpResponse implements HttpResponse<byte[]> {
    private int code;
    private Map<String, List<String>> headers;
    private String body;
    private String message;
    public TestHttpResponse(int code, Map<String, List<String>> headers, String body, String message) {
    this.code = code;
    this.headers = headers;
    this.body = body;
    this.message = message;
  }

    @Override
    public int code() { return code; }

    @Override
    public byte[] body() {
      if (StringUtils.isNotBlank(body)) {
        return body.getBytes();
      }
      return new byte[0];
    }

    @Override
    public HttpRequest request() { return null; }

    @Override
    public Optional<HttpResponse<?>> previousResponse() { return Optional.empty(); }

    @Override
    public List<String> headers(String s) { return headers.get(s); }

    @Override
    public Map<String, List<String>> headers() { return headers; }

    @Override
    public String message() { return message; }
}
