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
package org.eclipse.jkube.kit.config.image.build;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

class BuildPackConfigurationTest {
  @Test
  void mergeBuildPackConfigurationWithImageConfiguration_shouldMergeImageConfigurationFieldsWithBuildPackConfiguration() {
    // Given
    List<String> tags = Arrays.asList("t1", "t2");
    BuildPackConfiguration buildPackConfiguration = BuildPackConfiguration.builder()
        .build();
    Map<String, String> env = new HashMap<>();
    env.put("FOO", "BAR");
    List<String> volumes = Collections.singletonList("/tmp/volume:/platform/volume:ro");

    // When
    BuildPackConfiguration packConfiguration = BuildPackConfiguration.mergeBuildPackConfigurationWithImageConfiguration(buildPackConfiguration,
        "IfNotPresent", tags, env, volumes);

    // Then
    assertThat(packConfiguration)
        .hasFieldOrPropertyWithValue("tags", Arrays.asList("t1", "t2"))
        .hasFieldOrPropertyWithValue("env", Collections.singletonMap("FOO", "BAR"))
        .hasFieldOrPropertyWithValue("volumes", Collections.singletonList("/tmp/volume:/platform/volume:ro"));
  }

  @Test
  void createOpinionatedBuildPackConfiguration() {
    assertThat(BuildPackConfiguration.createOpinionatedBuildPackConfiguration())
        .hasFieldOrPropertyWithValue("builderImage", "paketobuildpacks/builder:base")
        .hasFieldOrPropertyWithValue("runImage", "paketobuildpacks/run:base-cnb")
        .hasFieldOrPropertyWithValue("creationTime", "now");
  }
}
