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
package org.eclipse.jkube.kit.config.service.kubernetes;

import org.eclipse.jkube.kit.build.service.docker.DockerServiceHub;
import org.eclipse.jkube.kit.build.service.docker.RegistryService;
import org.eclipse.jkube.kit.common.JKubeConfiguration;
import org.eclipse.jkube.kit.common.JavaProject;
import org.eclipse.jkube.kit.common.KitLogger;
import org.eclipse.jkube.kit.common.RegistryConfig;
import org.eclipse.jkube.kit.config.image.ImageConfiguration;
import org.eclipse.jkube.kit.config.image.build.BuildConfiguration;
import org.eclipse.jkube.kit.config.image.build.BuildPackConfiguration;
import org.eclipse.jkube.kit.config.image.build.JKubeBuildStrategy;
import org.eclipse.jkube.kit.config.service.JKubeServiceException;
import org.eclipse.jkube.kit.config.service.JKubeServiceHub;
import org.eclipse.jkube.kit.service.buildpacks.BuildPackBuildUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.MockedStatic;

import java.io.File;
import java.io.IOException;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BuildPackBuildServiceTest {
  @TempDir
  private File temporaryFolder;

  private KitLogger logger;

  private JKubeServiceHub mockedServiceHub;

  private ImageConfiguration imageConfiguration;
  private BuildPackConfiguration buildPackConfiguration;
  private RegistryService mockedRegistryService;

  private RegistryConfig registryConfig;

  private MockedStatic<BuildPackBuildUtil> buildPackBuildUtilMockedStatic;

  @BeforeEach
  void setUp() {
    logger = spy(new KitLogger.SilentLogger());
    mockedServiceHub = mock(JKubeServiceHub.class, RETURNS_DEEP_STUBS);
    DockerServiceHub mockedDockerServiceHub = mock(DockerServiceHub.class);
    mockedRegistryService = mock(RegistryService.class);
    JKubeConfiguration mockedJKubeConfiguration = JKubeConfiguration.builder()
        .project(JavaProject.builder()
            .baseDirectory(temporaryFolder)
            .build())
        .outputDirectory("target/docker")
        .build();
    when(mockedServiceHub.getLog()).thenReturn(logger);
    when(mockedServiceHub.getConfiguration()).thenReturn(mockedJKubeConfiguration);
    when(mockedServiceHub.getDockerServiceHub()).thenReturn(mockedDockerServiceHub);
    when(mockedDockerServiceHub.getRegistryService()).thenReturn(mockedRegistryService);
    buildPackBuildUtilMockedStatic = mockStatic(BuildPackBuildUtil.class);
    buildPackConfiguration = BuildPackConfiguration.builder()
            .builderImage("foo/builder:base")
            .runImage("foo/run:base")
            .creationTime("now")
            .build();
    imageConfiguration = ImageConfiguration.builder()
        .name("test/testimage:0.0.1")
        .build(BuildConfiguration.builder()
            .buildpack(buildPackConfiguration)
            .build())
        .build();
    registryConfig = RegistryConfig.builder()
        .authConfig(Collections.emptyMap())
        .settings(Collections.emptyList())
        .build();
  }

  @AfterEach
  void close() {
    buildPackBuildUtilMockedStatic.close();
  }

  @Test
  void isApplicable_withNoBuildStrategy_shouldReturnFalse() {
    // When
    final boolean result = new BuildPackBuildService(mockedServiceHub).isApplicable();
    // Then
    assertThat(result).isFalse();
  }

  @ParameterizedTest
  @CsvSource({
      "s2i,false", "jib,false", "docker,false", "buildpacks,true"
  })
  void isApplicable_withGivenStrategy_shouldReturnTrueOnlyForBuildPackStrategy(String buildStrategyValue, boolean expectedResult) {
    // Given
    when(mockedServiceHub.getBuildServiceConfig().getJKubeBuildStrategy()).thenReturn(JKubeBuildStrategy.valueOf(buildStrategyValue));
    // When
    final boolean result = new BuildPackBuildService(mockedServiceHub).isApplicable();
    // Then
    assertThat(result).isEqualTo(expectedResult);
  }

  @Test
  void buildSingleImage_whenBuildPackConfigurationProvided_thenBuildImage() {
    // Given
    BuildPackBuildService buildPackBuildService = new BuildPackBuildService(mockedServiceHub);

    // When
    buildPackBuildService.buildSingleImage(imageConfiguration);

    // Then
    buildPackBuildUtilMockedStatic.verify(() -> BuildPackBuildUtil.buildImage(logger, temporaryFolder, "target/docker", "test/testimage:0.0.1", buildPackConfiguration));
  }

  @Test
  void buildSingleImage_whenNoBuildConfiguration_thenNoBuild() {
    // Given
    imageConfiguration = imageConfiguration.toBuilder()
        .build(null)
        .build();
    BuildPackBuildService buildPackBuildService = new BuildPackBuildService(mockedServiceHub);

    // When
    buildPackBuildService.buildSingleImage(imageConfiguration);

    // Then
    buildPackBuildUtilMockedStatic.verify(() -> BuildPackBuildUtil.buildImage(eq(logger), eq(temporaryFolder), anyString(), anyString(), eq(buildPackConfiguration)), times(0));
  }

  @Test
  void pushSingleImage_whenImageConfigurationProvided_thenPushUsingRegistryService() throws JKubeServiceException, IOException {
    // Given
    BuildPackBuildService buildPackBuildService = new BuildPackBuildService(mockedServiceHub);

    // When
    buildPackBuildService.pushSingleImage(imageConfiguration, 0, registryConfig, false);

    // Then
    verify(mockedRegistryService, times(1)).pushImage(imageConfiguration, 0, registryConfig, false);
  }

  @Test
  void pushSingleImage_whenPushFailed_thenThrowException() throws IOException {
    // Given
    BuildPackBuildService buildPackBuildService = new BuildPackBuildService(mockedServiceHub);
    doThrow(new IOException("Failure in pushing image")).when(mockedRegistryService).pushImage(imageConfiguration, 0, registryConfig, false);

    // When + Then
    assertThatExceptionOfType(JKubeServiceException.class)
        .isThrownBy(() -> buildPackBuildService.pushSingleImage(imageConfiguration, 0, registryConfig, false))
        .withMessage("Error while trying to push the image: Failure in pushing image");
  }
}
