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

import java.io.File;
import java.net.MalformedURLException;
import java.util.Objects;
import java.util.Properties;

import org.eclipse.jkube.kit.common.KitLogger;
import org.eclipse.jkube.kit.common.RegistryConfig;
import org.eclipse.jkube.kit.common.util.EnvUtil;
import org.eclipse.jkube.kit.common.util.PropertiesUtil;
import org.eclipse.jkube.kit.config.image.ImageConfiguration;
import org.eclipse.jkube.kit.config.image.build.JKubeBuildStrategy;
import org.eclipse.jkube.kit.config.service.AbstractImageBuildService;
import org.eclipse.jkube.kit.config.service.BuildServiceConfig;
import org.eclipse.jkube.kit.config.service.JKubeServiceException;
import org.eclipse.jkube.kit.config.service.JKubeServiceHub;
import org.eclipse.jkube.kit.service.buildpacks.BuildPackBuildOptions;
import org.eclipse.jkube.kit.service.buildpacks.BuildPackCliDownloader;
import org.eclipse.jkube.kit.service.buildpacks.controller.BuildPackCliController;

import static org.apache.commons.lang3.StringUtils.strip;

public class BuildPackBuildService extends AbstractImageBuildService {
  private static final String DEFAULT_BUILDER_IMAGE = "paketobuildpacks/builder:base";
  public static final String PACK_CONFIG_DIR = ".pack";
  public static final String PACK_CONFIG_FILE = "config.toml";

  private final BuildServiceConfig buildServiceConfig;
  private final KitLogger kitLogger;

  public BuildPackBuildService(JKubeServiceHub jKubeServiceHub) {
    super(jKubeServiceHub);
    this.buildServiceConfig = Objects.requireNonNull(jKubeServiceHub.getBuildServiceConfig(),
        "BuildServiceConfig is required");
    this.kitLogger = Objects.requireNonNull(jKubeServiceHub.getLog());
  }

  @Override
  protected void buildSingleImage(ImageConfiguration imageConfiguration) {
    kitLogger.info("Delegating container image building process to BuildPacks");
    doBuildPackBuild(imageConfiguration);
  }

  private void doBuildPackBuild(ImageConfiguration imageConfiguration) {
    Properties packProperties = PropertiesUtil.getPropertiesFromResource(BuildPackBuildService.class.getResource("/META-INF/jkube/pack-cli.properties"));
    BuildPackCliDownloader packCliDownloader = new BuildPackCliDownloader(kitLogger, packProperties);
    BuildPackCliController packCliController = new BuildPackCliController(packCliDownloader.getPackCLIIfPresentOrDownload(), kitLogger);
    BuildPackBuildOptions buildOptions = createBuildPackOptions(imageConfiguration);
    packCliController.build(buildOptions);
  }

  private BuildPackBuildOptions createBuildPackOptions(ImageConfiguration imageConfiguration) {
    Properties packConfigProperties = readLocalPackConfig();
    String builderImage = strip(packConfigProperties.getProperty("default-builder-image", DEFAULT_BUILDER_IMAGE), "\"");
    BuildPackBuildOptions.BuildPackBuildOptionsBuilder buildOptionsBuilder = BuildPackBuildOptions.builder();
    buildOptionsBuilder.imageName(imageConfiguration.getName());
    buildOptionsBuilder.builderImage(builderImage);
    buildOptionsBuilder.creationTime("now");
    return buildOptionsBuilder.build();
  }

  private Properties readLocalPackConfig() {
    File packConfigDir = new File(EnvUtil.getUserHome(), PACK_CONFIG_DIR);
    if (packConfigDir.exists() && packConfigDir.isDirectory()) {
      File packConfig = new File(packConfigDir, PACK_CONFIG_FILE);
      try {
        return PropertiesUtil.getPropertiesFromResource(packConfig.toURI().toURL());
      } catch (MalformedURLException e) {
        kitLogger.warn("Failure in reading pack local configuration : " + e.getMessage());
      }
    }
    return new Properties();
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
