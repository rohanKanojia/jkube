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

import org.eclipse.jkube.kit.common.TestHttpStaticServer;
import org.eclipse.jkube.kit.common.assertj.FileAssertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.File;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThatIOException;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

class CliDownloaderUtilTest {
  @TempDir
  private File temporaryFolder;

  @Test
  @EnabledOnOs(OS.LINUX)
  void isCLIDownloadPlatformWindows_whenLinuxOs_thenReturnFalse() {
    assertThat(CliDownloaderUtil.isCliDownloadPlatformWindows()).isFalse();
  }

  @Test
  @EnabledOnOs(OS.LINUX)
  void getCLIDownloadPlatformApplicableBinary_whenLinuxOs_thenReturnFalse() {
    assertThat(CliDownloaderUtil.getCliDownloadPlatformApplicableBinary("foo")).isEqualTo("foo");
  }

  @ParameterizedTest
  @CsvSource({"amd64,false", "aarch64,true"})
  void isCLIDownloadPlatformProcessorArchitectureARM_whenArchitectureProvided_shouldReturnApplicableResult(String arch, boolean expectedResult) {
    String defaultArchValue = System.getProperty("os.arch");
    try {
      // Given
      System.setProperty("os.arch", arch);

      // When
      assertThat(CliDownloaderUtil.isCliDownloadPlatformProcessorArchitectureARM()).isEqualTo(expectedResult);

      // Then
    } finally {
      System.setProperty("os.arch", defaultArchValue);
    }
  }

  @Test
  void downloadCLI_whenUnixArtifactProvided_thenDownloadAndExtract() throws IOException {
    File remoteDirectory = new File(getClass().getResource("/downloadable-artifacts").getFile());
    try (TestHttpStaticServer http = new TestHttpStaticServer(remoteDirectory)) {
      // Given
      String baseUrl = String.format("http://localhost:%d", http.getPort());

      // When
      String downloadPath = CliDownloaderUtil.downloadCli(baseUrl, "foo", "foo-v0.0.1-linux.tgz", temporaryFolder);

      // Then
      assertThat(downloadPath).contains("foo-v0.0.1-linux", "foo");
      FileAssertions.assertThat(temporaryFolder)
          .exists()
          .fileTree()
          .containsExactlyInAnyOrder("foo-v0.0.1-linux", "foo-v0.0.1-linux/foo");
    }
  }

  @Test
  void downloadCLI_whenZipArtifactProvided_thenDownloadAndExtract() throws IOException {
    File remoteDirectory = new File(getClass().getResource("/downloadable-artifacts").getFile());
    try (TestHttpStaticServer http = new TestHttpStaticServer(remoteDirectory)) {
      // Given
      String baseUrl = String.format("http://localhost:%d", http.getPort());

      // When
      String downloadPath = CliDownloaderUtil.downloadCli(baseUrl, "foo", "foo-v0.0.1-windows.zip", temporaryFolder);

      // Then
      assertThat(downloadPath).contains("foo-v0.0.1-windows", "foo.exe");
      FileAssertions.assertThat(temporaryFolder)
          .exists()
          .fileTree()
          .containsExactlyInAnyOrder("foo-v0.0.1-windows", "foo-v0.0.1-windows/foo.exe");
    }
  }

  @Test
  void downloadCLI_whenArtifactNotAvailable_thenThrowException() throws IOException {
    File remoteDirectory = new File(getClass().getResource("/downloadable-artifacts").getFile());
    try (TestHttpStaticServer http = new TestHttpStaticServer(remoteDirectory)) {
      // Given
      String baseUrl = String.format("http://localhost:%d", http.getPort());

      // When + Then
      assertThatIOException()
          .isThrownBy(() -> CliDownloaderUtil.downloadCli(baseUrl, "idontexist", "idontexist-v0.0.1-linux.tgz", temporaryFolder))
          .withMessageContaining("Failed to download");
    }
  }
}
