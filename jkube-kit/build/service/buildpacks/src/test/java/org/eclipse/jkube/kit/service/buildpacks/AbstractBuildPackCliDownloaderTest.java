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

import org.eclipse.jkube.kit.common.KitLogger;
import org.eclipse.jkube.kit.common.util.GitHubCliDownloaderUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

abstract class AbstractBuildPackCliDownloaderTest {
  private BuildPackCliDownloader buildPackCliDownloader;
  private MockedStatic<GitHubCliDownloaderUtil> gitHubCliDownloaderUtilMockedStatic;
  @TempDir
  private File temporaryFolder;
  private File targetDir;
  private KitLogger kitLogger;
  abstract String getPlatform();
  abstract String getPlatformBinary();
  abstract String getExpectedDownloadArchiveName();
  abstract boolean isPlatformARMProcessorArchitecture();

  @BeforeEach
  void setUp() throws IOException {
    kitLogger = new KitLogger.SilentLogger();
    buildPackCliDownloader = new BuildPackCliDownloader(kitLogger);
    targetDir = new File(temporaryFolder, "target");
    Files.createDirectory(targetDir.toPath());
    gitHubCliDownloaderUtilMockedStatic = mockStatic(GitHubCliDownloaderUtil.class);
  }

  @AfterEach
  void tearDown() {
    gitHubCliDownloaderUtilMockedStatic.close();
  }

  @Test
  void getPackCLIIfPresentOrDownload_whenPackCLIAlreadyDownloaded_thenReturnPackCli() throws IOException {
    try (MockedConstruction<BuildPackVersionCommand> buildPackVersionCommandMockedConstruction = mockConstruction(BuildPackVersionCommand.class, (mock, ctx) -> {
      doNothing().when(mock).execute();
      when(mock.getVersion()).thenReturn("v0.32.1");
    })) {
      // Given
      givenDownloadPlatform(getPlatform());
      givenPackCLIAlreadyDownloaded();

      // When
      String downloadedCli = buildPackCliDownloader.getPackCLIIfPresentOrDownload(temporaryFolder, targetDir);

      // Then
      verifyPackCLINeverDownloaded();
      assertThat(buildPackVersionCommandMockedConstruction.constructed()).hasSize(1);
      assertThat(downloadedCli).isEqualTo("target/pack-v0.32.1-" + getPlatform() + "/pack");
    }
  }

  @Test
  void getPackCLIIfPresentOrDownload_whenPackCLIAlreadyDownloadedButCorrupt_thenDownloadAndReturnPackCli() throws IOException {
    try (MockedConstruction<BuildPackVersionCommand> buildPackVersionCommandMockedConstruction = mockConstruction(BuildPackVersionCommand.class, (mock, ctx) -> {
      doThrow(new IOException("pack CLI exec failure")).when(mock).execute();
    })) {
      // Given
      givenDownloadPlatform(getPlatform());
      givenPackCLIAlreadyDownloaded();
      givenPackCLIDownloadedToPath(targetDir.toPath().resolve("pack-v0.32.1-" + getPlatform()).resolve(getPlatformBinary()).toFile().getAbsolutePath());

      // When
      String downloadedCli = buildPackCliDownloader.getPackCLIIfPresentOrDownload(temporaryFolder, targetDir);

      // Then
      verifyPackCLIDownloadedOnce();
      assertThat(buildPackVersionCommandMockedConstruction.constructed()).hasSize(1);
      assertThat(downloadedCli).isEqualTo("target/pack-v0.32.1-" + getPlatform() + "/" + getPlatformBinary());
    }
  }

  @Test
  void getPackCLIIfPresentOrDownload_whenPackCLIAbsent_thenDownloadAndReturnPackCli() {
    // Given
    givenDownloadPlatform(getPlatform());
    givenPackCLIDownloadedToPath(targetDir.toPath().resolve("pack-v0.32.1-" + getPlatform()).resolve(getPlatformBinary()).toFile().getAbsolutePath());

    // When
    String downloadedCli = buildPackCliDownloader.getPackCLIIfPresentOrDownload(temporaryFolder, targetDir);

    // Then
    verifyPackCLIDownloadedOnce();
    assertThat(downloadedCli).isEqualTo("target/pack-v0.32.1-"+ getPlatform() + "/" + getPlatformBinary());
  }

  @Test
  void getPackCLIIfPresentOrDownload_whenPackCLIAbsentAndDownloadFailed_thenFallbackToLocalPackCli() {
    try (MockedConstruction<BuildPackVersionCommand> buildPackVersionCommandMockedConstruction = mockConstruction(BuildPackVersionCommand.class, (mock, ctx) -> {
      doNothing().when(mock).execute();
      when(mock.getVersion()).thenReturn("v0.32.1");
    })) {
      // Given
      givenApplicablePlatformBinary(getPlatformBinary());
      givenDownloadPlatform(getPlatform());
      givenPackCLIDownloadThrowsException();

      // When
      String downloadedCli = buildPackCliDownloader.getPackCLIIfPresentOrDownload(temporaryFolder, targetDir);

      // Then
      verifyPackCLIDownloadedOnce();
      assertThat(buildPackVersionCommandMockedConstruction.constructed()).hasSize(1);
      assertThat(downloadedCli).isEqualTo(getPlatformBinary());
    }
  }

  @Test
  void getPackCLIIfPresentOrDownload_whenPackCLIAbsentAndDownloadFailedAndNoPackBinary_thenThrowException() {
    try (MockedConstruction<BuildPackVersionCommand> buildPackVersionCommandMockedConstruction = mockConstruction(BuildPackVersionCommand.class, (mock, ctx) -> {
      doThrow(new IOException("exec error : no command found")).when(mock).execute();
    })) {
      // Given
      givenApplicablePlatformBinary(getPlatformBinary());
      givenDownloadPlatform(getPlatform());
      givenPackCLIDownloadThrowsException();

      // When + Then
      assertThatIllegalStateException()
          .isThrownBy(() -> buildPackCliDownloader.getPackCLIIfPresentOrDownload(temporaryFolder, targetDir))
          .withMessage("No local pack binary found");
      verifyPackCLIDownloadedOnce();
      assertThat(buildPackVersionCommandMockedConstruction.constructed()).hasSize(1);
    }
  }

  private void givenPackCLIAlreadyDownloaded() throws IOException {
    File extractedPackDir = new File(targetDir, "pack-v0.32.1-" + getPlatform());
    Files.createDirectory(extractedPackDir.toPath());
    File packCLI = new File(extractedPackDir, "pack");
    Files.createFile(packCLI.toPath());
    assertThat(packCLI.setExecutable(true)).isTrue();
  }

  private void givenPackCLIDownloadedToPath(String downloadLocation) {
    gitHubCliDownloaderUtilMockedStatic.when(
            () -> GitHubCliDownloaderUtil.downloadCLIFromGitHub(kitLogger,
                "https://github.com/buildpacks/pack/releases/download/v0.32.1",
                "pack", getExpectedDownloadArchiveName(), targetDir))
        .thenReturn(downloadLocation);
  }

  private void givenPackCLIDownloadThrowsException() {
    gitHubCliDownloaderUtilMockedStatic.when(
            () -> GitHubCliDownloaderUtil.downloadCLIFromGitHub(any(), anyString(), anyString(), anyString(), any()))
        .thenThrow(new IOException("Network error"));
  }

  private void verifyPackCLIDownloadedOnce() {
    gitHubCliDownloaderUtilMockedStatic.verify(
        () -> GitHubCliDownloaderUtil.downloadCLIFromGitHub(kitLogger,
            "https://github.com/buildpacks/pack/releases/download/v0.32.1",
            "pack", getExpectedDownloadArchiveName(), targetDir));
  }

  private void verifyPackCLINeverDownloaded() {
    gitHubCliDownloaderUtilMockedStatic.verify(
        () -> GitHubCliDownloaderUtil.downloadCLIFromGitHub(any(), anyString(), anyString(), anyString(), any()),
        times(0));
  }

  private void givenDownloadPlatform(String platform) {
    if (platform.equalsIgnoreCase("windows")) {
      gitHubCliDownloaderUtilMockedStatic.when(GitHubCliDownloaderUtil::isCLIDownloadPlatformWindows).thenReturn(true);
    }
    gitHubCliDownloaderUtilMockedStatic.when(GitHubCliDownloaderUtil::getCLIDownloadPlatform).thenReturn(platform);
    gitHubCliDownloaderUtilMockedStatic.when(GitHubCliDownloaderUtil::isCLIDownloadPlatformProcessorArchitectureARM).thenReturn(isPlatformARMProcessorArchitecture());
  }

  private void givenApplicablePlatformBinary(String binaryFileName) {
    gitHubCliDownloaderUtilMockedStatic.when(() -> GitHubCliDownloaderUtil.getCLIDownloadPlatformApplicableBinary(anyString())).thenReturn(binaryFileName);
  }
}
