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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.http.HttpClient;
import io.fabric8.kubernetes.client.http.HttpResponse;
import io.fabric8.kubernetes.client.http.StandardHttpHeaders;
import io.fabric8.kubernetes.client.utils.HttpClientUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.jkube.kit.common.HttpURLConnectionResponse;
import org.eclipse.jkube.kit.common.KitLogger;

import static org.apache.commons.io.IOUtils.EOF;
import static org.apache.commons.lang3.StringUtils.strip;

/**
 *
 * Utilities for download and more
 * @author roland
 * @since 14/10/16
 */
public class IoUtil {

    private static final Random RANDOM = new Random();

    private IoUtil() { }

    /**
     * Download with showing the progress a given URL and store it in a file
     * @param log logger used to track progress
     * @param downloadUrl url to download
     * @param target target file where to store the downloaded data
     * @throws IOException IO Exception
     */
    public static void download(KitLogger log, URL downloadUrl, File target) throws IOException {
        log.progressStart();
        try (HttpClient client = HttpClientUtils.createHttpClient(Config.empty())
            .newBuilder().readTimeout(30, TimeUnit.MINUTES).build()
        ) {
            final HttpResponse<InputStream> response = client.sendAsync(
                client.newHttpRequestBuilder().url(downloadUrl).build(), InputStream.class)
                .get();
            final int length = Integer.parseInt(response.headers(StandardHttpHeaders.CONTENT_LENGTH)
                .stream().findAny().orElse("-1"));
            try (OutputStream out = Files.newOutputStream(target.toPath()); InputStream is = response.body()) {
                final byte[] buffer = new byte[8192];
                long readBytes = 0;
                int len;
                while (EOF != (len = is.read(buffer))) {
                    readBytes += len;
                    log.progressUpdate(target.getName(), "Downloading", getProgressBar(readBytes, length));
                    out.write(buffer, 0, len);
                }
            }
        }  catch(InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("Download interrupted", ex);
        } catch (IOException | ExecutionException e) {
            throw new IOException("Failed to download URL " + downloadUrl + " to  " + target + ": " + e, e);
        } finally {
            log.progressFinished();
        }
    }

    /**
     * Find a free (on localhost) random port in the range [49152, 65535] after 100 attempts.
     *
     * @return a random port where a server socket can be bound to
     */
    public static int getFreeRandomPort() {
        // 100 attempts should be enough
        return getFreeRandomPort(49152, 65535, 100);
    }

    /**
     *
     * Find a free (on localhost) random port in the specified range after the given number of attempts.
     *
     * @param min minimum value for port
     * @param max maximum value for port
     * @param attempts number of attempts
     * @return random port as integer
     */
    public static int getFreeRandomPort(int min, int max, int attempts) {
        for (int i=0; i < attempts; i++) {
            int port = min + RANDOM.nextInt(max - min + 1);
            try (Socket ignored = new Socket("localhost", port)) { // NOSONAR
                // Port is open for communication, meaning it's used up, try again
            } catch (ConnectException e) {
                return port;
            } catch (IOException e) {
                throw new IllegalStateException("Error while trying to check open ports", e);
            }
        }
        throw new IllegalStateException("Cannot find a free random port in the range [" + min + ", " + max + "] after " + attempts + " attempts");
    }

    /**
     * Returns an identifier from the given string that can be used as file name.
     *
     * @param name file name
     * @return sanitized file name
     */
    public static String sanitizeFileName(String name) {
        if (name != null) {
            return name.replaceAll("[^A-Za-z0-9]+", "-");
        }

        return null;
    }

    public static String getHeaderValueFromHeaders(Map<String, List<String>> headers, String key) {
        String headerValue = null;
        if (headers.containsKey(key)) {
            headerValue = headers.get(key).get(0);
        } else if (headers.containsKey(key.toLowerCase(Locale.ROOT))) {
            headerValue = headers.get(key.toLowerCase(Locale.ROOT)).get(0);
        }
        return headerValue;
    }

    public static String createFormDataStringFromMap(Map<String, String> formData) throws UnsupportedEncodingException {
        StringBuilder result = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, String> e : formData.entrySet()) {
            if (first) {
                first = false;
            } else {
                result.append("&");
            }
            result.append(URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8.name()));
            result.append("=");
            result.append(URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8.name()));
        }
        return result.toString();
    }

    public static Map<String, String> parseWwwAuthenticateHeaderToMap(String wwwAuthenticateHeader) {
        String[] wwwAuthenticateHeaders = wwwAuthenticateHeader.split(",");
        Map<String, String> result = new HashMap<>();
        for (String challenge : wwwAuthenticateHeaders) {
            if (challenge.contains("=")) {
                String[] challengeParts = challenge.split("=");
                if (challengeParts.length == 2) {
                    result.put(challengeParts[0], strip(challengeParts[1], "\""));
                }
            }
        }
        return result;
    }

    public static HttpURLConnectionResponse doHttpRequest(KitLogger logger, String method, String url, Map<String, String> headers) throws IOException {
        return doHttpRequest(logger, method, url, headers, null, null);
    }

    public static HttpURLConnectionResponse doHttpRequest(KitLogger logger, String method, String url, Map<String, String> headers, String requestBodyPayload, File fileToUpload) throws IOException {
        final HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        logger.verbose("%s %s", method, url);
        connection.setDoOutput(true);
        connection.setRequestMethod(method);
        logger.debug("request headers:");
        for (Map.Entry<String, String> header : headers.entrySet()) {
            logger.debug(header.getKey(), header.getValue());
            connection.setRequestProperty(header.getKey(), header.getValue());
        }
        if (fileToUpload != null) {
            try (FileInputStream fileInputStream = new FileInputStream(fileToUpload)) {
                IOUtils.copy(fileInputStream, connection.getOutputStream());
            }
        } else if (StringUtils.isNotBlank(requestBodyPayload)) {
            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = requestBodyPayload.getBytes(Charset.defaultCharset());
                os.write(input, 0, input.length);
            }
        }

        int responseCode = connection.getResponseCode();

        logger.debug(Integer.toString(responseCode));
        HttpURLConnectionResponse.HttpURLConnectionResponseBuilder responseBuilder = HttpURLConnectionResponse.builder();
        responseBuilder.code(responseCode);
        if (StringUtils.isNotBlank(connection.getResponseMessage())) {
            responseBuilder.message(connection.getResponseMessage());
        }
        if (connection.getHeaderFields() != null && !connection.getHeaderFields().isEmpty()) {
            logger.debug("Response Headers");
            logger.debug(connection.getHeaderFields().toString());
            responseBuilder.headers(connection.getHeaderFields());
        }
        if (connection.getErrorStream() != null) {
            responseBuilder.error(IOUtils.toString(connection.getErrorStream(), Charset.defaultCharset()));
        }
        if (isResponseSuccessful(responseCode) && connection.getInputStream() != null) {
            responseBuilder.body(IOUtils.toString(connection.getInputStream(), Charset.defaultCharset()));
        }
        connection.disconnect();
        return responseBuilder.build();
    }

    public static String appendQueryParam(String originalUrl, String key, String value) throws UnsupportedEncodingException {
        char queryToken = '?';
        if (originalUrl.indexOf(queryToken) >= 0) {
            queryToken = '&';
        }
        return originalUrl + queryToken + key + "=" + URLEncoder.encode(value, "UTF-8");
    }

    public static boolean isResponseSuccessful(int responseCode) {
        return responseCode < HttpURLConnection.HTTP_MULT_CHOICE;
    }

    // ========================================================================================

    private static final int PROGRESS_LENGTH = 50;

    private static String getProgressBar(long bytesRead, long length) {
        StringBuilder ret = new StringBuilder("[");
        if (length > - 1) {
            int bucketSize = (int) ((double)length / PROGRESS_LENGTH + 0.5D);
            int index = (int) ((double)bytesRead / bucketSize + 0.5D);
            for (int i = 0; i < PROGRESS_LENGTH; i++) {
                ret.append(i < index ? "=" : (i == index ? ">" : " "));
            }
            ret.append(String.format("] %.2f MB/%.2f MB",
                    ((float) bytesRead / (1024 * 1024)),
                    ((float) length / (1024 * 1024))));
        } else {
            int bucketSize = 200 * 1024; // 200k
            int index = (int) ((double)bytesRead / bucketSize + 0.5D) % PROGRESS_LENGTH;
            for (int i = 0; i < PROGRESS_LENGTH; i++) {
                ret.append(i == index ? "*" : " ");
            }
            ret.append("]");
        }

        return ret.toString();
    }
}
