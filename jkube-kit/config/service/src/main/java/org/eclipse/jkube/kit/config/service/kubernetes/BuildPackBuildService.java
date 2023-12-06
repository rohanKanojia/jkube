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

import java.util.Objects;

import org.eclipse.jkube.kit.common.KitLogger;
import org.eclipse.jkube.kit.common.RegistryConfig;
import org.eclipse.jkube.kit.config.image.ImageConfiguration;
import org.eclipse.jkube.kit.config.image.build.JKubeBuildStrategy;
import org.eclipse.jkube.kit.config.service.AbstractImageBuildService;
import org.eclipse.jkube.kit.config.service.BuildServiceConfig;
import org.eclipse.jkube.kit.config.service.JKubeServiceException;
import org.eclipse.jkube.kit.config.service.JKubeServiceHub;
import org.eclipse.jkube.kit.service.buildpacks.PackCliBuildOrchestrator;

public class BuildPackBuildService extends AbstractImageBuildService {

  private final BuildServiceConfig buildServiceConfig;
  private final KitLogger kitLogger;
  private final PackCliBuildOrchestrator packCliBuildOrchestrator;

  public BuildPackBuildService(JKubeServiceHub jKubeServiceHub) {
    super(jKubeServiceHub);
    this.buildServiceConfig = Objects.requireNonNull(jKubeServiceHub.getBuildServiceConfig(),
        "BuildServiceConfig is required");
    this.kitLogger = Objects.requireNonNull(jKubeServiceHub.getLog());
    this.packCliBuildOrchestrator = new PackCliBuildOrchestrator(kitLogger);
  }

  @Override
  protected void buildSingleImage(ImageConfiguration imageConfiguration) {
    kitLogger.info("Delegating container image building process to BuildPacks");
    if (imageConfiguration.getBuild() != null) {
      packCliBuildOrchestrator.buildImage(imageConfiguration);
    } else {
      kitLogger.info("No Build Configuration found");
    }
  }

  @Override
  protected void pushSingleImage(ImageConfiguration imageConfiguration, int retries, RegistryConfig registryConfig, boolean skipTag) throws JKubeServiceException {
  }

  @Override
  public boolean isApplicable() {
    return buildServiceConfig.getJKubeBuildStrategy() != null &&
        buildServiceConfig.getJKubeBuildStrategy().equals(JKubeBuildStrategy.buildpacks);
  }

  @Override
  public void postProcess() {
    // NOOP
  }
}
