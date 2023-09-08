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
import org.eclipse.jkube.kit.config.image.build.BuildPackConfiguration;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BuildPackBuildUtilTest {
  private KitLogger kitLogger;
  @TempDir
  private File temporaryFolder;
  private File outputDir;
  private String imageName;
  private MockedStatic<GitHubCliDownloaderUtil> gitHubCliDownloaderUtilMockedStatic;

  @BeforeEach
  void setUp() throws IOException {
    kitLogger = spy(new KitLogger.SilentLogger());
    outputDir = new File(temporaryFolder, "target");
    Files.createDirectory(outputDir.toPath());
    imageName = "test/foo:latest";
    gitHubCliDownloaderUtilMockedStatic = mockStatic(GitHubCliDownloaderUtil.class);
  }

  @AfterEach
  void tearDown() {
    gitHubCliDownloaderUtilMockedStatic.close();
  }

  @Test
  void buildImage_whenPackAbsent_thenDownloadAndRunPackCommand() throws IOException {
    try (MockedConstruction<BuildPackBuildImageCommand> buildPackBuildImageCommandMockedConstruction = mockConstruction(BuildPackBuildImageCommand.class)) {
      // Given
      gitHubCliDownloaderUtilMockedStatic.when(() -> GitHubCliDownloaderUtil.downloadCLIFromGitHub(eq(kitLogger), anyString(), anyString(), anyString(), anyString(), anyString(), any(File.class)))
          .thenReturn("target/docker/pack-v0.31.0-linux/pack");
      gitHubCliDownloaderUtilMockedStatic.when(GitHubCliDownloaderUtil::getDownloadPlatform).thenReturn("linux");

      // When
      BuildPackBuildUtil.buildImage(kitLogger, temporaryFolder, "target", imageName, createNewBuildPackConfiguration());

      // Then
      gitHubCliDownloaderUtilMockedStatic.verify(() -> GitHubCliDownloaderUtil.downloadCLIFromGitHub(kitLogger,
          "https://github.com/buildpacks/pack/releases/download/v0.31.0", "linux", "pack", "v0.31.0", ".tgz", outputDir));
      assertThat(buildPackBuildImageCommandMockedConstruction.constructed()).hasSize(1);
      BuildPackBuildImageCommand buildPackBuildImageCommand = buildPackBuildImageCommandMockedConstruction.constructed().get(0);
      verify(buildPackBuildImageCommand).execute();
    }
  }

  @Test
  void buildImage_whenPackAlreadyPresent_thenDirectlyRunPackCommand() throws IOException {
    try (MockedConstruction<BuildPackBuildImageCommand> buildPackBuildImageCommandMockedConstruction = mockConstruction(BuildPackBuildImageCommand.class)) {
      // Given
      givenPackCliAlreadyDownloaded();
      gitHubCliDownloaderUtilMockedStatic.when(GitHubCliDownloaderUtil::getDownloadPlatform).thenReturn("linux");

      // When
      BuildPackBuildUtil.buildImage(kitLogger, temporaryFolder, "target", imageName, createNewBuildPackConfiguration());

      // Then
      gitHubCliDownloaderUtilMockedStatic.verify(() -> GitHubCliDownloaderUtil.downloadCLIFromGitHub(eq(kitLogger),
          anyString(), anyString(), anyString(), anyString(), anyString(), any(File.class)), times(0));
      assertThat(buildPackBuildImageCommandMockedConstruction.constructed()).hasSize(1);
      BuildPackBuildImageCommand buildPackBuildImageCommand = buildPackBuildImageCommandMockedConstruction.constructed().get(0);
      verify(buildPackBuildImageCommand).execute();
    }
  }

  @Test
  void buildImage_whenPackCliExecutionFailed_thenThrowException() throws IOException {
    try (MockedConstruction<BuildPackBuildImageCommand> buildPackBuildImageCommandMockedConstruction = mockConstruction(BuildPackBuildImageCommand.class, (mock, ctx) -> {
      doThrow(new IOException("pack cli execution error")).when(mock).execute();
    })) {
      // Given
      givenPackCliAlreadyDownloaded();
      gitHubCliDownloaderUtilMockedStatic.when(GitHubCliDownloaderUtil::getDownloadPlatform).thenReturn("linux");

      // When + Then
      assertThatIllegalStateException()
          .isThrownBy(() -> BuildPackBuildUtil.buildImage(kitLogger, temporaryFolder, "target", imageName, createNewBuildPackConfiguration()))
          .withMessage("Failure in executing pack command");
      assertThat(buildPackBuildImageCommandMockedConstruction.constructed()).hasSize(1);
      BuildPackBuildImageCommand buildPackBuildImageCommand = buildPackBuildImageCommandMockedConstruction.constructed().get(0);
      verify(buildPackBuildImageCommand).execute();
    }
  }

  @Test
  void buildImage_whenPackCliDownloadFailed_thenUseLocalPackCli() throws IOException {
    try (MockedConstruction<BuildPackBuildImageCommand> buildPackBuildImageCommandMockedConstruction = mockConstruction(BuildPackBuildImageCommand.class);
         MockedConstruction<BuildPackVersionCommand> buildPackVersionCommandMockedConstruction = mockConstruction(BuildPackVersionCommand.class, (mock, ctx) -> {
           when(mock.getVersion()).thenReturn("v0.31.0");
         })) {
      // Given
      gitHubCliDownloaderUtilMockedStatic.when(GitHubCliDownloaderUtil::getDownloadPlatform).thenReturn("linux");
      gitHubCliDownloaderUtilMockedStatic.when(() -> GitHubCliDownloaderUtil.downloadCLIFromGitHub(eq(kitLogger), anyString(), anyString(), anyString(), anyString(), anyString(), any(File.class)))
          .thenThrow(new IOException("Network exception"));
      gitHubCliDownloaderUtilMockedStatic.when(() -> GitHubCliDownloaderUtil.getApplicableBinary("pack")).thenReturn("pack");

      // When
      BuildPackBuildUtil.buildImage(kitLogger, temporaryFolder, "target", imageName, createNewBuildPackConfiguration());

      // Then
      assertThat(buildPackBuildImageCommandMockedConstruction.constructed()).hasSize(1);
      assertThat(buildPackVersionCommandMockedConstruction.constructed()).hasSize(1);
      BuildPackBuildImageCommand buildPackBuildImageCommand = buildPackBuildImageCommandMockedConstruction.constructed().get(0);
      verify(buildPackBuildImageCommand).execute();
    }
  }

  @Test
  void buildImage_whenPackCliDownloadFailedAndNoLocalPackCli_thenThrowException() {
    try (MockedConstruction<BuildPackVersionCommand> buildPackVersionCommandMockedConstruction = mockConstruction(BuildPackVersionCommand.class, (mock, ctx) -> {
           doThrow(new IOException("Failed to run pack")).when(mock).execute();
         })) {
      // Given
      gitHubCliDownloaderUtilMockedStatic.when(GitHubCliDownloaderUtil::getDownloadPlatform).thenReturn("linux");
      gitHubCliDownloaderUtilMockedStatic.when(() -> GitHubCliDownloaderUtil.downloadCLIFromGitHub(eq(kitLogger), anyString(), anyString(), anyString(), anyString(), anyString(), any(File.class)))
          .thenThrow(new IOException("Network exception"));
      gitHubCliDownloaderUtilMockedStatic.when(() -> GitHubCliDownloaderUtil.getApplicableBinary("pack")).thenReturn("pack");

      // When + Then
      assertThatIllegalStateException()
          .isThrownBy(() -> BuildPackBuildUtil.buildImage(kitLogger, temporaryFolder, "target", imageName, createNewBuildPackConfiguration()))
          .withMessage("No local pack binary found, Not able to download pack CLI : Network exception");
      assertThat(buildPackVersionCommandMockedConstruction.constructed()).hasSize(1);
    }
  }

  private BuildPackConfiguration createNewBuildPackConfiguration() {
    return BuildPackConfiguration.builder()
        .builderImage("foo/builder:base")
        .build();
  }

  private void givenPackCliAlreadyDownloaded() throws IOException {
    File extractedPackDir = new File(outputDir, "pack-v0.31.0-linux");
    Files.createDirectory(extractedPackDir.toPath());
    File packCli = new File(extractedPackDir, "pack");
    Files.createFile(packCli.toPath());
    assertThat(packCli.setExecutable(true)).isTrue();
  }
}
