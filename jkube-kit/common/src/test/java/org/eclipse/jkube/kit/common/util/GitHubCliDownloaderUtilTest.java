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
    assertThat(GitHubCliDownloaderUtil.getDownloadPlatform()).isEqualTo("linux");
  }

  @Test
  void downloadCLIFromGitHub_whenUnixArtifactProvided_thenDownloadAndExtract() throws IOException {
    File remoteDirectory = new File(getClass().getResource("/downloadable-artifacts").getFile());
    try (TestHttpStaticServer http = new TestHttpStaticServer(remoteDirectory)) {
      // Given
      String baseUrl = String.format("http://localhost:%d", http.getPort());

      // When
      GitHubCliDownloaderUtil.downloadCLIFromGitHub(kitLogger, baseUrl, "linux", "pack", "v0.31.0", ".tgz", temporaryFolder);

      // Then
      FileAssertions.assertThat(temporaryFolder)
          .exists()
          .fileTree()
          .containsExactlyInAnyOrder("pack-v0.31.0-linux", "pack-v0.31.0-linux/pack", "pack-v0.31.0-linux.tgz");
    }
  }

  @Test
  void downloadCLIFromGitHub_whenZipArtifactProvided_thenDownloadAndExtract() throws IOException {
    File remoteDirectory = new File(getClass().getResource("/downloadable-artifacts").getFile());
    try (TestHttpStaticServer http = new TestHttpStaticServer(remoteDirectory)) {
      // Given
      String baseUrl = String.format("http://localhost:%d", http.getPort());

      // When
      GitHubCliDownloaderUtil.downloadCLIFromGitHub(kitLogger, baseUrl, "windows", "pack", "v0.31.0", ".zip", temporaryFolder);

      // Then
      FileAssertions.assertThat(temporaryFolder)
          .exists()
          .fileTree()
          .containsExactlyInAnyOrder("pack-v0.31.0-windows", "pack-v0.31.0-windows/pack.exe", "pack-v0.31.0-windows.zip");
    }
  }
}
