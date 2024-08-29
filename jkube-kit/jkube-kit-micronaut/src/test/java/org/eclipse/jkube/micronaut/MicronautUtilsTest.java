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
package org.eclipse.jkube.micronaut;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import org.eclipse.jkube.kit.common.JavaProject;
import org.eclipse.jkube.kit.common.KitLogger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.eclipse.jkube.micronaut.MicronautUtils.extractPort;
import static org.eclipse.jkube.micronaut.MicronautUtils.hasNativeImagePackaging;
import static org.eclipse.jkube.micronaut.MicronautUtils.isHealthEnabled;
import static org.eclipse.jkube.micronaut.MicronautUtils.resolveMicronautApplicationConfigProperties;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class MicronautUtilsTest {
  @TempDir
  private Path temporaryFolder;
  private KitLogger kitLogger;

  @BeforeEach
  void setUp() {
    kitLogger = spy(new KitLogger.SilentLogger());
  }

  @Test
  void extractPortWithPort() {
    // Given
    final Properties properties = new Properties();
    properties.put("micronaut.server.port", "1337");
    // When
    final String result = extractPort(properties, "80");
    // Then
    assertThat(result).isEqualTo("1337");
  }

  @Test
  void extractPortWithNoPort() {
    // When
    final String result = extractPort(new Properties(), "80");
    // Then
    assertThat(result).isEqualTo("80");
  }

  @Test
  void isHealthEnabledWithDefaults() {
    // When
    final boolean result = isHealthEnabled(new Properties());
    // Then
    assertThat(result).isFalse();
  }

  @Test
  void isHealthEnabledWithHealthEnabled() {
    // Given
    final Properties properties = new Properties();
    properties.put("endpoints.health.enabled", "tRuE");
    // When
    final boolean result = isHealthEnabled(properties);
    // Then
    assertThat(result).isTrue();
  }

  @Test
  void resolveMicronautApplicationConfigPropertiesPrecedence() throws IOException {
    // Given
    JavaProject javaProject = JavaProject.builder()
      .compileClassPathElement(MicronautUtilsTest.class.getResource("/utils-test/port-config/json/").getPath())
      .compileClassPathElement(MicronautUtilsTest.class.getResource("/utils-test/port-config/yaml/").getPath())
      .compileClassPathElement(MicronautUtilsTest.class.getResource("/utils-test/port-config/properties/").getPath())
      .outputDirectory(Files.createDirectory(temporaryFolder.resolve("target")).toFile())
      .build();
    // When
    final Properties props = resolveMicronautApplicationConfigProperties(kitLogger, javaProject);
    // Then
    assertThat(props).containsExactly(
        entry("micronaut.application.name", "port-config-test-PROPERTIES"),
        entry("micronaut.server.port", "1337"));
    verify(kitLogger, times(1)).debug("Micronaut Application Config loaded from : %s", MicronautUtilsTest.class.getResource("/utils-test/port-config/properties/application.properties"));
  }

  @Test
  void resolveMicronautApplicationConfigPropertiesNoConfigFiles() throws IOException {
    // Given
    JavaProject javaProject = JavaProject.builder()
      .compileClassPathElement("/")
      .outputDirectory(Files.createDirectory(temporaryFolder.resolve("target")).toFile())
      .build();
    // When
    final Properties props = resolveMicronautApplicationConfigProperties(kitLogger, javaProject);
    // Then
    assertThat(props).isEmpty();
    verify(kitLogger, times(0)).debug(anyString(), any());
  }

  @ParameterizedTest
  @CsvSource(value = {
    "native-image,true",
    "jar,false"
  })
  void hasNativeImagePackaging_whenPackagingProvided_thenShouldReturnExpectedResult(String packaging, boolean expectedValue) {
    // Given
    Properties properties = new Properties();
    properties.put("packaging", packaging);
    JavaProject javaProject = JavaProject.builder()
      .properties(properties)
      .build();
    // When + Then
    assertThat(hasNativeImagePackaging(javaProject)).isEqualTo(expectedValue);
  }

  @Test
  void hasNativeImagePackaging_whenNativeImagePluginProvided_thenShouldReturnExpectedResult() {
    // Given
    JavaProject javaProject = JavaProject.builder()
      .gradlePlugin("org.graalvm.buildtools.gradle.NativeImagePlugin")
      .build();
    // When + Then
    assertThat(hasNativeImagePackaging(javaProject)).isTrue();
  }
}
