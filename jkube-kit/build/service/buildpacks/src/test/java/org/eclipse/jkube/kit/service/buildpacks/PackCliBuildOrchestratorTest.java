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
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

import org.eclipse.jkube.kit.common.KitLogger;
import org.eclipse.jkube.kit.common.TestHttpStaticServer;
import org.eclipse.jkube.kit.common.util.EnvUtil;
import org.eclipse.jkube.kit.config.image.ImageConfiguration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

class PackCliBuildOrchestratorTest {
  private static final String TEST_PACK_VERSION = "v0.32.1";
  private PackCliBuildOrchestrator buildOrchestrator;
  private KitLogger kitLogger;
  private ImageConfiguration imageConfiguration;
  private String applicablePackBinary;

  @TempDir
  private File temporaryFolder;

  @BeforeEach
  void setUp() {
    kitLogger = spy(new KitLogger.SilentLogger());
    imageConfiguration = ImageConfiguration.builder()
        .name("foo/bar:latest")
        .build();
    applicablePackBinary = EnvUtil.isWindows() ? "pack.bat" : "pack";
    Map<String, String> properties = new HashMap<>();
    properties.put("user.home", temporaryFolder.getAbsolutePath());
    properties.put("os.name", System.getProperty("os.name"));
    properties.put("os.arch", System.getProperty("os.arch"));
    Map<String, String> env = Collections.singletonMap("HOME", temporaryFolder.getAbsolutePath());
    EnvUtil.overrideEnvGetter(env::get);
    EnvUtil.overridePropertyGetter(properties::get);
  }

  @AfterEach
  void tearDown() {
    EnvUtil.overrideEnvGetter(System::getenv);
    EnvUtil.overridePropertyGetter(System::getProperty);
  }

  @Test
  void buildImage_whenPackConfigHasDefaultBuilderSet_thenUseThatBuilder() throws IOException {
    File remoteDirectory = new File(Objects.requireNonNull(getClass().getResource("/artifacts")).getFile());
    try (TestHttpStaticServer http = new TestHttpStaticServer(remoteDirectory)) {
      // Given
      givenPackConfigHasDefaultBuilder(temporaryFolder);
      buildOrchestrator = createPackCliBuildOrchestrator(String.format("http://localhost:%d/", http.getPort()));

      // When
      buildOrchestrator.buildImage(imageConfiguration);

      // Then
      verify(kitLogger).info("[[s]]%s","build foo/bar:latest --builder cnbs/sample-builder:bionic --creation-time now");
    }
  }

  @Test
  void buildImage_whenNoDefaultBuilderInPackConfig_thenUseOpinionatedBuilderImage() throws IOException {
    File remoteDirectory = new File(Objects.requireNonNull(getClass().getResource("/artifacts")).getFile());
    try (TestHttpStaticServer http = new TestHttpStaticServer(remoteDirectory)) {
      // Given
      buildOrchestrator = createPackCliBuildOrchestrator(String.format("http://localhost:%d/", http.getPort()));

      // When
      buildOrchestrator.buildImage(imageConfiguration);

      // Then
      verify(kitLogger).info("[[s]]%s", "build foo/bar:latest --builder paketobuildpacks/builder:base --creation-time now");
    }
  }

  private PackCliBuildOrchestrator createPackCliBuildOrchestrator(String baseUrl) {
    Properties packProperties = createPackProperties(baseUrl);
    return new PackCliBuildOrchestrator(kitLogger, packProperties);
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
