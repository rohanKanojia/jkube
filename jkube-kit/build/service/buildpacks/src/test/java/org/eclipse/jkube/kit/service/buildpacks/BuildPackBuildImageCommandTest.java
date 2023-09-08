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
import org.eclipse.jkube.kit.config.image.build.BuildPackConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

class BuildPackBuildImageCommandTest {
  private KitLogger kitLogger;

  @BeforeEach
  void setUp() {
    kitLogger = spy(new KitLogger.SilentLogger());
  }

  @Test
  void getArgs_whenEmptyBuildPackConfiguration_thenGenerateCompleteCommand() {
    // Given
    String imageName = "test/foo:latest";
    String cliPath = "target/docker/pack-v0.31.0-linux/pack";
    BuildPackConfiguration buildPackConfiguration = BuildPackConfiguration.builder()
        .build();
    BuildPackBuildImageCommand buildPackBuildImageCommand = new BuildPackBuildImageCommand(kitLogger, imageName, cliPath, buildPackConfiguration);

    // When + Then
    assertThat(buildPackBuildImageCommand.getArgs())
        .containsExactly(cliPath, "build", "test/foo:latest");
  }

  @Test
  void getArgs_whenCompleteBuildPackConfiguration_thenGenerateCompleteCommand() {
    // Given
    String imageName = "test/foo:latest";
    String cliPath = "target/docker/pack-v0.31.0-linux/pack";
    BuildPackConfiguration buildPackConfiguration = BuildPackConfiguration.builder()
        .publish(true)
        .clearCache(true)
        .trustBuilder(true)
        .dockerHost("/var/run/docker.sock")
        .cacheImage("foo/cache-image:latest")
        .cache("type=launch;format=image;source=test/image:latest")
        .path("/home/example/basedir")
        .registry("example-registry.io")
        .network("default")
        .pullPolicy("if-not-present")
        .descriptor("/home/example/basedir/target/project.toml<")
        .defaultProcess("web")
        .lifecycleImage("buildpacksio/lifecycle:latest")
        .putEnv("BP_SPRING_CLOUD_BINDINGS_DISABLED", "true")
        .putEnv("BPL_SPRING_CLOUD_BINDINGS_DISABLED", "true")
        .envFile("/home/example/basedir/src/main/resources/application.properties")
        .buildpack("samples/java-maven")
        .extension("test-extension")
        .volume("/tmp/volume:/platform/volume:ro")
        .tags(Arrays.asList("t1", "t2", "t3"))
        .workspace("/platform/application-overridden-dir")
        .gid(1000)
        .previousImage("test-previous-image")
        .sbomOutputDir("/home/example/basedir/target/sbom-output-dir")
        .reportOutputDir("/home/example/basedir/target/buildpack-build-report.toml")
        .creationTime("now")
        .builderImage("paketobuildpacks/builder:base")
        .runImage("paketobuildpacks/run:base-cnb")
        .preBuildpack("paketobuildpacks/java-memory-assistant:latest")
        .postBuildpack("paketobuildpacks/appdynamics:latest")
        .build();
    BuildPackBuildImageCommand buildPackBuildImageCommand = new BuildPackBuildImageCommand(kitLogger, imageName, cliPath, buildPackConfiguration);

    // When + Then
    assertThat(buildPackBuildImageCommand.getArgs())
        .containsExactly(cliPath, "build", "test/foo:latest",
            "--builder", "paketobuildpacks/builder:base",  "--buildpack-registry", "example-registry.io",
            "--cache", "type=launch;format=image;source=test/image:latest", "--cache-image", "foo/cache-image:latest",
            "--creation-time", "now", "--default-process", "web",
            "--descriptor", "/home/example/basedir/target/project.toml<", "--docker-host", "/var/run/docker.sock",
            "--lifecycle-image", "buildpacksio/lifecycle:latest", "--network", "default",
            "--path", "/home/example/basedir", "--previous-image", "test-previous-image",
            "--pull-policy", "if-not-present", "--report-output-dir", "/home/example/basedir/target/buildpack-build-report.toml",
            "--run-image", "paketobuildpacks/run:base-cnb", "--sbom-output-dir", "/home/example/basedir/target/sbom-output-dir",
            "--workspace", "/platform/application-overridden-dir", "--clear-cache", "--publish", "--trust-builder",
            "--env-file", "/home/example/basedir/src/main/resources/application.properties",
            "--post-buildpack", "paketobuildpacks/appdynamics:latest", "--pre-buildpack", "paketobuildpacks/java-memory-assistant:latest",
            "--volume", "/tmp/volume:/platform/volume:ro", "--buildpack", "samples/java-maven",
            "--extension", "test-extension",
            "--env", "BP_SPRING_CLOUD_BINDINGS_DISABLED=true", "--env", "BPL_SPRING_CLOUD_BINDINGS_DISABLED=true",
            "--gid", "1000",
            "--tag", "test/foo:t1", "--tag", "test/foo:t2", "--tag", "test/foo:t3");
  }

  @Test
  void processLine_whenInvoked_shouldLogOutput() {
    // Given
    String imageName = "test/foo:latest";
    String cliPath = "target/docker/pack-v0.31.0-linux/pack";
    BuildPackConfiguration buildPackConfiguration = BuildPackConfiguration.builder()
        .build();
    BuildPackBuildImageCommand buildPackBuildImageCommand = new BuildPackBuildImageCommand(kitLogger, imageName, cliPath, buildPackConfiguration);

    // When
    buildPackBuildImageCommand.processLine("===> ANALYZING");
    buildPackBuildImageCommand.processLine("===> DETECTING");
    buildPackBuildImageCommand.processLine("===> RESTORING");
    buildPackBuildImageCommand.processLine("===> EXPORTING");

    // Then
    verify(kitLogger).info("[[s]]%s", "===> ANALYZING");
    verify(kitLogger).info("[[s]]%s", "===> DETECTING");
    verify(kitLogger).info("[[s]]%s", "===> RESTORING");
    verify(kitLogger).info("[[s]]%s", "===> EXPORTING");
  }
}
