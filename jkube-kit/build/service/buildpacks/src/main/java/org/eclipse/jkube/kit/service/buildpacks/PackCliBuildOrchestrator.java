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
import org.eclipse.jkube.kit.common.util.PropertiesUtil;
import org.eclipse.jkube.kit.config.image.ImageConfiguration;
import org.eclipse.jkube.kit.service.buildpacks.controller.BuildPackCliController;
import org.eclipse.jkube.kit.service.buildpacks.controller.BuildPackController;

import java.io.File;
import java.net.MalformedURLException;
import java.util.Properties;

import static org.apache.commons.lang3.StringUtils.strip;

public class PackCliBuildOrchestrator {
  private static final String DEFAULT_CLI_PROPERTIES_RESOURCE = "/META-INF/jkube/pack-cli.properties";
  private static final String DEFAULT_BUILDER_IMAGE = "paketobuildpacks/builder:base";
  public static final String PACK_CONFIG_DIR = ".pack";
  public static final String PACK_CONFIG_FILE = "config.toml";
  private final KitLogger kitLogger;
  private final BuildPackController buildPackController;

  public PackCliBuildOrchestrator(KitLogger kitLogger) {
    this(kitLogger, PropertiesUtil.getPropertiesFromResource(PackCliBuildOrchestrator.class.getResource(DEFAULT_CLI_PROPERTIES_RESOURCE)));
  }

  PackCliBuildOrchestrator(KitLogger kitLogger, Properties packCliProperties) {
    this.kitLogger = kitLogger;
    BuildPackCliDownloader buildPackCliDownloader = new BuildPackCliDownloader(kitLogger, packCliProperties);
    this.buildPackController = new BuildPackCliController(buildPackCliDownloader.getPackCLIIfPresentOrDownload(), kitLogger);
  }

  public void buildImage(ImageConfiguration imageConfiguration) {
    BuildPackBuildOptions buildOptions = createBuildPackOptions(imageConfiguration);
    buildPackController.build(buildOptions);
  }

  private BuildPackBuildOptions createBuildPackOptions(ImageConfiguration imageConfiguration) {
    BuildPackBuildOptions.BuildPackBuildOptionsBuilder buildOptionsBuilder = new BuildPackBuildOptions.BuildPackBuildOptionsBuilder();
    buildOptionsBuilder.imageName(imageConfiguration.getName());
    buildOptionsBuilder.builderImage(resolveBuilderImage());
    buildOptionsBuilder.creationTime("now");
    return buildOptionsBuilder.build();
  }

  private String resolveBuilderImage() {
    Properties packConfigProperties = readLocalPackConfig();
    return strip(packConfigProperties.getProperty("default-builder-image", DEFAULT_BUILDER_IMAGE), "\"");
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
}
