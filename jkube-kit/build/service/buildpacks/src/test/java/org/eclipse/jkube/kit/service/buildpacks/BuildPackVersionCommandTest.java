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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

class BuildPackVersionCommandTest {
  private KitLogger kitLogger;

  @BeforeEach
  void setUp() {
    kitLogger = new KitLogger.SilentLogger();
  }

  @Test
  void getArgs() {
    // Given
    BuildPackVersionCommand buildPackVersionCommand = new BuildPackVersionCommand(kitLogger);

    // When + Then
    assertThat(buildPackVersionCommand.getArgs())
        .containsExactly("pack", "--version");
  }

  @Test
  void processLine_whenInvoked_shouldSetVersion() {
    // Given
    BuildPackVersionCommand buildPackVersionCommand = new BuildPackVersionCommand(kitLogger);
    // When
    buildPackVersionCommand.processLine("0.30.0");
    // Then
    assertThat(buildPackVersionCommand.getVersion()).isEqualTo("0.30.0");
  }
}
