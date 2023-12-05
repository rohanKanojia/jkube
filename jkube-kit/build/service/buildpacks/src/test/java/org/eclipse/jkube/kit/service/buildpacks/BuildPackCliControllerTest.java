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
import org.eclipse.jkube.kit.common.util.EnvUtil;
import org.eclipse.jkube.kit.service.buildpacks.controller.BuildPackCliController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

class BuildPackCliControllerTest {
  private KitLogger kitLogger;
  private String applicablePackBinary;
  private String invalidPackBinary;

  @BeforeEach
  void setUp() {
    kitLogger = spy(new KitLogger.SilentLogger());
    applicablePackBinary = EnvUtil.isWindows() ? "pack.bat" : "pack";
    invalidPackBinary = EnvUtil.isWindows() ? "invalid-pack.bat" : "invalid-pack";
  }

  @Test
  void build_whenCommandSuccessful_thenLogOutputToKitLogger() {
    // Given
    BuildPackCliController buildPackCliController = createBuildPackCliController(String.format("/%s", applicablePackBinary));
    BuildPackBuildOptions buildOptions = createBuildPackBuildOptions();

    // When
    buildPackCliController.build(buildOptions);

    // Then
    verify(kitLogger).info("[[s]]%s", "build foo/bar:latest --builder foo/builder:base --creation-time now");
  }

  @Test
  void build_whenCommandFailed_thenThrowException() {
    // Given
    BuildPackCliController buildPackCliController = createBuildPackCliController(String.format("/%s", invalidPackBinary));
    BuildPackBuildOptions buildOptions = createBuildPackBuildOptions();

    // When + Then
    assertThatIllegalStateException()
        .isThrownBy(() -> buildPackCliController.build(buildOptions))
        .withMessageContaining("Process Existed With : 1");
  }

  @Test
  void version_whenCommandSuccessful_thenReturnVersion() {
    // Given
    BuildPackCliController buildPackCliController = createBuildPackCliController(String.format("/%s", applicablePackBinary));

    // When
    String version = buildPackCliController.version();

    // Then
    assertThat(version).isEqualTo("0.32.1+git-b14250b.build-5241");
  }

  @Test
  void version_whenCommandFailed_thenReturnNull() {
    // Given
    BuildPackCliController buildPackCliController = createBuildPackCliController(String.format("/%s", invalidPackBinary));

    // When
    String version = buildPackCliController.version();

    // Then
    assertThat(version).isNull();
  }

  private BuildPackCliController createBuildPackCliController(String binaryResource) {
    File pack = new File(Objects.requireNonNull(getClass().getResource(binaryResource)).getFile());
    return new BuildPackCliController(pack, kitLogger);
  }

  private BuildPackBuildOptions createBuildPackBuildOptions() {
    return BuildPackBuildOptions.builder()
        .imageName("foo/bar:latest")
        .builderImage("foo/builder:base")
        .creationTime("now")
        .build();
  }
}
