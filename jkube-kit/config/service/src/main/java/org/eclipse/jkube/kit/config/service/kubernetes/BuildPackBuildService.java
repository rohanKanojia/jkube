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

import java.io.IOException;
import java.util.Objects;

import org.eclipse.jkube.kit.build.service.docker.DockerServiceHub;
import org.eclipse.jkube.kit.common.JKubeConfiguration;
import org.eclipse.jkube.kit.common.KitLogger;
import org.eclipse.jkube.kit.common.RegistryConfig;
import org.eclipse.jkube.kit.config.image.ImageConfiguration;
import org.eclipse.jkube.kit.config.image.build.JKubeBuildStrategy;
import org.eclipse.jkube.kit.config.service.AbstractImageBuildService;
import org.eclipse.jkube.kit.config.service.BuildServiceConfig;
import org.eclipse.jkube.kit.config.service.JKubeServiceException;
import org.eclipse.jkube.kit.config.service.JKubeServiceHub;

import static org.eclipse.jkube.kit.service.buildpacks.BuildPackBuildUtil.buildImage;

public class BuildPackBuildService extends AbstractImageBuildService {

  private final JKubeConfiguration jKubeConfiguration;
  private final DockerServiceHub dockerServices;
  private final BuildServiceConfig buildServiceConfig;
  private final KitLogger kitLogger;

  public BuildPackBuildService(JKubeServiceHub jKubeServiceHub) {
    super(jKubeServiceHub);
    this.jKubeConfiguration = Objects.requireNonNull(jKubeServiceHub.getConfiguration(),
        "JKubeConfiguration is required");
    this.dockerServices = Objects.requireNonNull(jKubeServiceHub.getDockerServiceHub(),
        "Docker Service Hub is required");
    this.buildServiceConfig = Objects.requireNonNull(jKubeServiceHub.getBuildServiceConfig(),
        "BuildServiceConfig is required");
    this.kitLogger = Objects.requireNonNull(jKubeServiceHub.getLog());
  }

  @Override
  protected void buildSingleImage(ImageConfiguration imageConfiguration) {
    kitLogger.info("Delegating container image building process to Buildpacks");
    if (imageConfiguration.getBuild() != null) {
      buildImage(kitLogger, jKubeConfiguration.getBasedir(), jKubeConfiguration.getOutputDirectory(), imageConfiguration.getName(), imageConfiguration.getBuildConfiguration().getBuildpack());
    } else {
      kitLogger.info("No BuildPack Configuration found");
    }
  }

  @Override
  protected void pushSingleImage(ImageConfiguration imageConfiguration, int retries, RegistryConfig registryConfig, boolean skipTag) throws JKubeServiceException {
    try {
      dockerServices.getRegistryService().pushImage(imageConfiguration, retries, registryConfig, skipTag);
    } catch (IOException ex) {
      throw new JKubeServiceException("Error while trying to push the image: " + ex.getMessage(), ex);
    }
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
