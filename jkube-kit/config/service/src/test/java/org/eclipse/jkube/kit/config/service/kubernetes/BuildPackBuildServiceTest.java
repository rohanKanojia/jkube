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

import org.eclipse.jkube.kit.config.image.build.JKubeBuildStrategy;
import org.eclipse.jkube.kit.config.service.JKubeServiceHub;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BuildPackBuildServiceTest {
  private JKubeServiceHub mockedServiceHub;

  @BeforeEach
  void setUp() {
    mockedServiceHub = mock(JKubeServiceHub.class, RETURNS_DEEP_STUBS);
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
}
