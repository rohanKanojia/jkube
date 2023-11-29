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
package org.eclipse.jkube.kit.service.buildpacks;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.jkube.kit.common.KitLogger;

import org.eclipse.jkube.kit.common.TestHttpStaticServer;
import org.eclipse.jkube.kit.common.util.EnvUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static java.nio.file.StandardCopyOption.COPY_ATTRIBUTES;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

abstract class AbstractBuildPackCliDownloaderTest {
  private static final String TEST_PACK_VERSION = "v0.32.1";
  private KitLogger kitLogger;
  @TempDir
  private File temporaryFolder;
  private Map<String, String> overriddenSystemProperties;
  private Map<String, String> overriddenEnvironmentVariables;
  private String applicablePackBinary;
  private String invalidApplicablePackBinary;

  abstract String getPlatform();
  abstract boolean isPlatformARMProcessorArchitecture();

  @BeforeEach
  void setUp() {
    kitLogger = new KitLogger.SilentLogger();
    overriddenSystemProperties = new HashMap<>();
    overriddenEnvironmentVariables = new HashMap<>();
    applicablePackBinary = EnvUtil.isWindows() ? "pack.bat" : "pack";
    invalidApplicablePackBinary = EnvUtil.isWindows() ? "invalid-pack.bat" : "invalid-pack";
    EnvUtil.overridePropertyGetter(s -> overriddenSystemProperties.get(s));
    EnvUtil.overrideEnvGetter(s -> overriddenEnvironmentVariables.get(s));
    overriddenSystemProperties.put("user.home", temporaryFolder.getAbsolutePath());
    overriddenEnvironmentVariables.put("HOME", temporaryFolder.getAbsolutePath());
    overriddenEnvironmentVariables.put("PATH", temporaryFolder.toPath().resolve("bin").toFile().getAbsolutePath());
  }

  @AfterEach
  void tearDown() {
    EnvUtil.overridePropertyGetter(System::getProperty);
    EnvUtil.overrideEnvGetter(System::getenv);
  }

  @Test
  void getPackCLIIfPresentOrDownload_whenPackCLIAlreadyDownloaded_thenReturnPackCli() throws IOException {
    // Given
    givenDownloadPlatform(getPlatform());
    givenPackCLIAlreadyDownloaded(String.format("/%s", applicablePackBinary));
    BuildPackCliDownloader buildPackCliDownloader = createNewBuildPackCliDownloader(null);

    // When
    File downloadedCli = buildPackCliDownloader.getPackCLIIfPresentOrDownload();

    // Then
    assertThat(downloadedCli.toPath()).isEqualTo(temporaryFolder.toPath().resolve(".jkube").resolve(applicablePackBinary));
  }

  @Test
  void getPackCLIIfPresentOrDownload_whenPackCLIAlreadyDownloadedButCorrupt_thenDownloadAndReturnPackCli() throws IOException {
    File remoteDirectory = new File(Objects.requireNonNull(getClass().getResource("/artifacts")).getFile());
    try (TestHttpStaticServer http = new TestHttpStaticServer(remoteDirectory)) {
      // Given
      givenDownloadPlatform(getPlatform());
      givenPackCLIAlreadyDownloaded(String.format("/%s", invalidApplicablePackBinary));
      BuildPackCliDownloader buildPackCliDownloader = createNewBuildPackCliDownloader(String.format("http://localhost:%d/", http.getPort()));

      // When
      File downloadedCli = buildPackCliDownloader.getPackCLIIfPresentOrDownload();

      // Then
      assertThat(downloadedCli.toPath()).isEqualTo(temporaryFolder.toPath().resolve(".jkube").resolve(applicablePackBinary));
    }
  }

  @Test
  void getPackCLIIfPresentOrDownload_whenPackCLIAbsent_thenDownloadAndReturnPackCli() throws IOException {
    File remoteDirectory = new File(Objects.requireNonNull(getClass().getResource("/artifacts")).getFile());
    try (TestHttpStaticServer http = new TestHttpStaticServer(remoteDirectory)) {
      // Given
      givenDownloadPlatform(getPlatform());
      BuildPackCliDownloader buildPackCliDownloader = createNewBuildPackCliDownloader(String.format("http://localhost:%d/", http.getPort()));

      // When
      File downloadedCli = buildPackCliDownloader.getPackCLIIfPresentOrDownload();

      // Then
      assertThat(downloadedCli.toPath()).isEqualTo(temporaryFolder.toPath().resolve(".jkube").resolve(applicablePackBinary));
    }
  }

  @Test
  void getPackCLIIfPresentOrDownload_whenPackCLIAbsentAndDownloadFailed_thenFallbackToLocalPackCli() throws IOException {
    File remoteDirectory = new File(Objects.requireNonNull(getClass().getResource("/invalid-artifacts")).getFile());
    try (TestHttpStaticServer http = new TestHttpStaticServer(remoteDirectory)) {
      // Given
      givenPackCliPresentOnUserMachine(String.format("/%s", applicablePackBinary));
      givenDownloadPlatform(getPlatform());
      BuildPackCliDownloader buildPackCliDownloader = createNewBuildPackCliDownloader(String.format("http://localhost:%d/", http.getPort()));

      // When
      File downloadedCli = buildPackCliDownloader.getPackCLIIfPresentOrDownload();

      // Then
      assertThat(downloadedCli).isEqualTo(temporaryFolder.toPath().resolve("bin").resolve(applicablePackBinary).toFile());
    }
  }

  @Test
  void getPackCLIIfPresentOrDownload_whenDownloadFailedAndLocalPackCliCorrupt_thenThrowException() throws IOException {
    File remoteDirectory = new File(Objects.requireNonNull(getClass().getResource("/invalid-artifacts")).getFile());
    try (TestHttpStaticServer http = new TestHttpStaticServer(remoteDirectory)) {
      // Given
      givenPackCliPresentOnUserMachine(String.format("/%s", invalidApplicablePackBinary));
      givenDownloadPlatform(getPlatform());
      BuildPackCliDownloader buildPackCliDownloader = createNewBuildPackCliDownloader(String.format("http://localhost:%d/", http.getPort()));

      // When + Then
      assertThatIllegalStateException()
          .isThrownBy(buildPackCliDownloader::getPackCLIIfPresentOrDownload)
          .withMessage("No local pack binary found");
    }
  }

  @Test
  void getPackCLIIfPresentOrDownload_whenPackCLIAbsentAndDownloadFailedAndNoPackBinary_thenThrowException() throws IOException {
    File remoteDirectory = new File(Objects.requireNonNull(getClass().getResource("/invalid-artifacts")).getFile());
    try (TestHttpStaticServer http = new TestHttpStaticServer(remoteDirectory)) {
      // Given
      givenDownloadPlatform(getPlatform());
      BuildPackCliDownloader buildPackCliDownloader = createNewBuildPackCliDownloader(String.format("http://localhost:%d/", http.getPort()));

      // When + Then
      assertThatIllegalStateException()
          .isThrownBy(buildPackCliDownloader::getPackCLIIfPresentOrDownload)
          .withMessage("No local pack binary found");
    }
  }

  private void givenPackCliPresentOnUserMachine(String packResource) throws IOException {
    File bin = new File(temporaryFolder, "bin");
    File pack = new File(Objects.requireNonNull(getClass().getResource(packResource)).getFile());
    Files.createDirectory(bin.toPath());
    Files.copy(pack.toPath(), bin.toPath().resolve(pack.getName()), COPY_ATTRIBUTES);
  }

  private void givenPackCLIAlreadyDownloaded(String alreadyDownloadedResource) throws IOException {
    File jKubeDownloadDir = new File(temporaryFolder, ".jkube");
    File pack = new File(Objects.requireNonNull(getClass().getResource(alreadyDownloadedResource)).getFile());
    Files.createDirectory(jKubeDownloadDir.toPath());
    Files.copy(pack.toPath(), jKubeDownloadDir.toPath().resolve(pack.getName()), COPY_ATTRIBUTES);
  }

  private BuildPackCliDownloader createNewBuildPackCliDownloader(String baseUrl) {
    if (StringUtils.isBlank(baseUrl)) {
      baseUrl = "https://example.com";
    }
    Properties packProperties = createPackProperties(baseUrl);
    return new BuildPackCliDownloader(kitLogger, packProperties);
  }

  private Properties createPackProperties(String baseUrl) {
    Properties properties = new Properties();
    properties.put("unix.binary-name", applicablePackBinary);
    properties.put("windows.binary-name", applicablePackBinary);
    properties.put("version", TEST_PACK_VERSION);
    properties.put("linux.artifact", baseUrl + "pack-" + TEST_PACK_VERSION + "-linux.tgz");
    properties.put("linux-arm64.artifact", baseUrl +  "pack-" + TEST_PACK_VERSION + "-linux-arm64.tgz");
    properties.put("macos.artifact", baseUrl +  "pack-" + TEST_PACK_VERSION + "-macos.tgz");
    properties.put("macos-arm64.artifact", baseUrl +  "pack-" + TEST_PACK_VERSION + "-macos-arm64.tgz");
    properties.put("windows.artifact", baseUrl +  "pack-" + TEST_PACK_VERSION + "-windows.zip");
    return properties;
  }

  private void givenDownloadPlatform(String platform) {
    if (platform.equals("linux")) {
      overriddenSystemProperties.put("os.name", "Linux");
    } else if (platform.equals("macos")) {
      overriddenSystemProperties.put("os.name", "Mac OS X");
    } else {
      overriddenSystemProperties.put("os.name", "Windows 11");
    }
    if (isPlatformARMProcessorArchitecture()) {
      overriddenSystemProperties.put("os.arch", "aarch64");
    }
  }
}
