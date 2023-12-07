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

import org.eclipse.jkube.kit.common.KitLogger;
import org.eclipse.jkube.kit.common.TestHttpStaticServer;
import org.eclipse.jkube.kit.common.util.EnvUtil;
import org.eclipse.jkube.kit.common.util.PropertiesUtil;
import org.eclipse.jkube.kit.config.image.ImageConfiguration;
import org.eclipse.jkube.kit.config.image.build.BuildConfiguration;
import org.eclipse.jkube.kit.config.image.build.JKubeBuildStrategy;
import org.eclipse.jkube.kit.config.service.JKubeServiceHub;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.MockedStatic;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BuildPackBuildServiceTest {
  private KitLogger kitLogger;
  private JKubeServiceHub mockedServiceHub;
  private static final String TEST_PACK_VERSION = "v0.32.1";
  private String applicablePackBinary;
  private ImageConfiguration imageConfiguration;
  private MockedStatic<PropertiesUtil> propertiesUtilMockedStatic;

  @TempDir
  private File temporaryFolder;

  @BeforeEach
  void setUp() {
    mockedServiceHub = mock(JKubeServiceHub.class, RETURNS_DEEP_STUBS);
    kitLogger = spy(new KitLogger.SilentLogger());
    when(mockedServiceHub.getLog()).thenReturn(kitLogger);
    imageConfiguration = ImageConfiguration.builder()
        .name("foo/bar:latest")
        .build(BuildConfiguration.builder()
            .from("foo/base:latest")
            .build())
        .build();
    applicablePackBinary = EnvUtil.isWindows() ? "pack.bat" : "pack";
    Map<String, String> properties = new HashMap<>();
    properties.put("user.home", temporaryFolder.getAbsolutePath());
    properties.put("os.name", System.getProperty("os.name"));
    properties.put("os.arch", System.getProperty("os.arch"));
    Map<String, String> env = Collections.singletonMap("HOME", temporaryFolder.getAbsolutePath());
    EnvUtil.overrideEnvGetter(env::get);
    EnvUtil.overridePropertyGetter(properties::get);
    propertiesUtilMockedStatic = mockStatic(PropertiesUtil.class);
  }

  @AfterEach
  void tearDown() {
    EnvUtil.overrideEnvGetter(System::getenv);
    EnvUtil.overridePropertyGetter(System::getProperty);
    propertiesUtilMockedStatic.close();
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
  void buildImage_whenPackConfigHasDefaultBuilderSet_thenUseThatBuilder() throws IOException {
    File remoteDirectory = new File(Objects.requireNonNull(getClass().getResource("/artifacts")).getFile());
    try (TestHttpStaticServer http = new TestHttpStaticServer(remoteDirectory)) {
      // Given
      givenPackConfigHasDefaultBuilder(temporaryFolder);
      propertiesUtilMockedStatic.when(() -> PropertiesUtil.getPropertiesFromResource(any()))
          .thenReturn(createPackProperties(String.format("http://localhost:%d/", http.getPort())))
          .thenCallRealMethod();

      // When
      new BuildPackBuildService(mockedServiceHub).buildSingleImage(imageConfiguration);

      // Then
      verify(kitLogger).info("[[s]]%s","build foo/bar:latest --builder cnbs/sample-builder:bionic --creation-time now");
    }
  }

  @Test
  void buildImage_whenNoDefaultBuilderInPackConfig_thenUseOpinionatedBuilderImage() throws IOException {
    File remoteDirectory = new File(Objects.requireNonNull(getClass().getResource("/artifacts")).getFile());
    try (TestHttpStaticServer http = new TestHttpStaticServer(remoteDirectory)) {
      propertiesUtilMockedStatic.when(() -> PropertiesUtil.getPropertiesFromResource(any()))
          .thenReturn(createPackProperties(String.format("http://localhost:%d/", http.getPort())));

      // When
      new BuildPackBuildService(mockedServiceHub).buildSingleImage(imageConfiguration);

      // Then
      verify(kitLogger).info("[[s]]%s", "build foo/bar:latest --builder paketobuildpacks/builder:base --creation-time now");
    }
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

  private void givenPackConfigHasDefaultBuilder(File userHome) throws IOException {
    File packHome = new File(userHome, ".pack");
    Files.createDirectory(packHome.toPath());
    File packConfig = new File(packHome, "config.toml");
    Files.write(packConfig.toPath(), String.format("default-builder-image=\"%s\"", "cnbs/sample-builder:bionic").getBytes());
  }
}
