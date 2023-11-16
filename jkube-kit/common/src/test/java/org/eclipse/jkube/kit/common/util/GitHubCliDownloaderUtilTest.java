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

import org.eclipse.jkube.kit.common.KitLogger;
import org.eclipse.jkube.kit.common.TestHttpStaticServer;
import org.eclipse.jkube.kit.common.assertj.FileAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.File;
import java.io.IOException;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

class GitHubCliDownloaderUtilTest {
  private KitLogger kitLogger;
  @TempDir
  private File temporaryFolder;

  @BeforeEach
  void setup() {
    kitLogger = new KitLogger.SilentLogger();
  }

  @Test
  @EnabledOnOs(OS.LINUX)
  void getDownloadPlatform_whenLinuxOs_thenReturnLinuxPlatform() {
    assertThat(GitHubCliDownloaderUtil.getCLIDownloadPlatform()).isEqualTo("linux");
  }

  @Test
  @EnabledOnOs(OS.LINUX)
  void isCLIDownloadPlatformWindows_whenLinuxOs_thenReturnFalse() {
    assertThat(GitHubCliDownloaderUtil.isCLIDownloadPlatformWindows()).isFalse();
  }

  @Test
  @EnabledOnOs(OS.LINUX)
  void getCLIDownloadPlatformApplicableBinary_whenLinuxOs_thenReturnFalse() {
    assertThat(GitHubCliDownloaderUtil.getCLIDownloadPlatformApplicableBinary("foo")).isEqualTo("foo");
  }

  @ParameterizedTest
  @CsvSource({"amd64,false", "aarch64,true"})
  void isCLIDownloadPlatformProcessorArchitectureARM_whenArchitectureProvided_shouldReturnApplicableResult(String arch, boolean expectedResult) {
    String defaultArchValue = System.getProperty("os.arch");
    try {
      // Given
      System.setProperty("os.arch", arch);

      // When
      assertThat(GitHubCliDownloaderUtil.isCLIDownloadPlatformProcessorArchitectureARM()).isEqualTo(expectedResult);

      // Then
    } finally {
      System.setProperty("os.arch", defaultArchValue);
    }
  }

  @Test
  void downloadCLIFromGitHub_whenUnixArtifactProvided_thenDownloadAndExtract() throws IOException {
    File remoteDirectory = new File(getClass().getResource("/downloadable-artifacts").getFile());
    try (TestHttpStaticServer http = new TestHttpStaticServer(remoteDirectory)) {
      // Given
      String baseUrl = String.format("http://localhost:%d", http.getPort());

      // When
      String downloadPath = GitHubCliDownloaderUtil.downloadCLIFromGitHub(kitLogger, baseUrl, "foo", "foo-v0.0.1-linux.tgz", temporaryFolder);

      // Then
      assertThat(downloadPath).contains("foo-v0.0.1-linux", "foo");
      FileAssertions.assertThat(temporaryFolder)
          .exists()
          .fileTree()
          .containsExactlyInAnyOrder("foo-v0.0.1-linux", "foo-v0.0.1-linux/foo", "foo-v0.0.1-linux.tgz");
    }
  }

  @Test
  void downloadCLIFromGitHub_whenZipArtifactProvided_thenDownloadAndExtract() throws IOException {
    File remoteDirectory = new File(getClass().getResource("/downloadable-artifacts").getFile());
    try (TestHttpStaticServer http = new TestHttpStaticServer(remoteDirectory)) {
      // Given
      String baseUrl = String.format("http://localhost:%d", http.getPort());

      // When
      String downloadPath = GitHubCliDownloaderUtil.downloadCLIFromGitHub(kitLogger, baseUrl, "foo", "foo-v0.0.1-windows.zip", temporaryFolder);

      // Then
      assertThat(downloadPath).contains("foo-v0.0.1-windows", "foo.exe");
      FileAssertions.assertThat(temporaryFolder)
          .exists()
          .fileTree()
          .containsExactlyInAnyOrder("foo-v0.0.1-windows", "foo-v0.0.1-windows/foo.exe", "foo-v0.0.1-windows.zip");
    }
  }
}
