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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Objects;
import java.util.Properties;

import org.eclipse.jkube.kit.common.JavaProject;
import org.eclipse.jkube.kit.common.KitLogger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.eclipse.jkube.kit.common.util.ThorntailUtil.resolveThorntailAppConfigProperties;
import static org.eclipse.jkube.kit.common.util.ThorntailUtil.resolveThorntailWebPortFromThorntailConfig;
import static org.mockito.Mockito.spy;

class ThorntailUtilTest {
  private KitLogger logger;

  @BeforeEach
  void setUp() {
    logger = spy(new KitLogger.SilentLogger());
  }

  @Test
  void resolveThorntailAppConfigProperties_whenProjectDefaultsYamlFileProvided_thenLoadApplicationConfig(@TempDir Path temporaryFolder) throws IOException {
    // Given
    JavaProject javaProject = JavaProject.builder()
      .compileClassPathElements(Collections.singletonList(
        Objects.requireNonNull(getClass().getResource("/util/")).getPath()))
      .outputDirectory(Files.createDirectory(temporaryFolder.resolve("target")).toFile())
      .build();
    // When
    Properties props = resolveThorntailAppConfigProperties(logger, javaProject);
    assertThat(props).isNotNull()
            .containsOnly(entry("thorntail.http.port", "8082"));
  }

  @Test
  void resolveThorntailWebPortFromThorntailConfig_whenApplicationConfigPropertiesContainPort_thenUseThatPort() {
    // Given
    Properties properties = new Properties();
    properties.put("thorntail.http.port", "8001");
    // When
    String webPort = resolveThorntailWebPortFromThorntailConfig(properties);
    // Then
    assertThat(webPort).isEqualTo("8001");
  }

  @Test
  void resolveThorntailWebPortFromThorntailConfig_whenSystemPropertiesContainPort_thenUseThatPort() {
    try {
      // Given
      Properties properties = new Properties();
      System.setProperty("thorntail.http.port", "8001");
      properties.put("thorntail.http.port", "8002");
      // When
      String webPort = resolveThorntailWebPortFromThorntailConfig(properties);
      // Then
      assertThat(webPort).isEqualTo("8001");
    } finally {
      System.clearProperty("thorntail.http.port");
    }
  }

  @Test
  void resolveThorntailWebPortFromThorntailConfig_whenSystemPropertiesAndProjectApplicationConfigurationContainPort_thenUseThatPort() {
    try {
      // Given
      Properties properties = new Properties();
      System.setProperty("thorntail.http.port", "8001");
      // When
      String webPort = resolveThorntailWebPortFromThorntailConfig(properties);
      // Then
      assertThat(webPort).isEqualTo("8001");
    } finally {
      System.clearProperty("thorntail.http.port");
    }
  }
}
