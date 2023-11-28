/*
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

import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.http.HttpClient;
import io.fabric8.kubernetes.client.http.HttpResponse;
import io.fabric8.kubernetes.client.utils.HttpClientUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.eclipse.jkube.kit.common.ProxyConfig;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URL;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static io.fabric8.kubernetes.client.utils.HttpClientUtils.basicCredentials;
import static org.apache.commons.io.FilenameUtils.removeExtension;
import static org.eclipse.jkube.kit.common.archive.ArchiveDecompressor.extractArchive;

public class CliDownloaderUtil {
  private CliDownloaderUtil() { }

  public static String downloadCli(String baseDownloadUrl, String binaryPrefix, String artifactName, File outputDirectory, ProxyConfig proxyConfig) throws IOException {
    URL downloadUrl = new URL(String.format("%s/%s", baseDownloadUrl, artifactName));
    File targetExtractionDir = outputDirectory.toPath().resolve(removeExtension(artifactName)).toFile();

    downloadAndExtractTo(downloadUrl, targetExtractionDir, proxyConfig);

    return getCliBinaryPathFromExtractedDir(targetExtractionDir, binaryPrefix);
  }

  private static void downloadAndExtractTo(URL downloadUrl, File target, ProxyConfig proxyConfig) throws IOException {
    HttpClient.Builder httpClientBuilder = HttpClientUtils.getHttpClientFactory().newBuilder(Config.empty());
    if (proxyConfig != null) {
      httpClientBuilder.proxyAddress(new InetSocketAddress(proxyConfig.getHost(), proxyConfig.getPort()));
      if (StringUtils.isNotBlank(proxyConfig.getNonProxyHosts()) && downloadUrl.getHost().matches(proxyConfig.getNonProxyHosts())) {
        httpClientBuilder.proxyType(HttpClient.ProxyType.DIRECT);
      } else {
        httpClientBuilder.proxyType(HttpClient.ProxyType.HTTP);
      }
      httpClientBuilder.proxyAuthorization(basicCredentials(proxyConfig.getUsername(), proxyConfig.getPassword()));
    }
    try (HttpClient client = httpClientBuilder.build()) {
      final HttpResponse<InputStream> response = client.sendAsync(
              client.newHttpRequestBuilder().timeout(1, TimeUnit.SECONDS).url(downloadUrl).build(), InputStream.class)
          .get();
      if (!response.isSuccessful()) {
        throw new IOException("Server returned (" + response.code() + ") while downloading " + downloadUrl);
      }
      try (InputStream is = response.body()) {
        extractArchive(is, target);
      }
    } catch(InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new IOException("Download interrupted", ex);
    } catch (IOException | ExecutionException e) {
      throw new IOException("Failed to download URL " + downloadUrl + " to  " + target + ": " + e, e);
    }
  }

  private static String getCliBinaryPathFromExtractedDir(File targetExtractionDir, String binaryPrefix) {
    String cliPath = null;
    File[] cliArtifactList = targetExtractionDir.listFiles(f -> f.getName().startsWith(binaryPrefix));
    if (cliArtifactList != null && cliArtifactList.length >= 1) {
      cliPath = cliArtifactList[0].getAbsolutePath();
    }
    return cliPath;
  }

  public static boolean isCliDownloadPlatformWindows() {
    return SystemUtils.IS_OS_WINDOWS;
  }

  public static String getCliDownloadPlatformApplicableBinary(String binaryName) {
    return isCliDownloadPlatformWindows() ? binaryName + ".exe" : binaryName;
  }

  public static boolean isCliDownloadPlatformProcessorArchitectureARM() {
    String architecture = System.getProperty("os.arch");
    return StringUtils.isNotBlank(architecture) && architecture.contains("aarch");
  }
}