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

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Singular;
import org.apache.commons.lang3.StringUtils;

import java.io.Serializable;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
@Getter
@EqualsAndHashCode(doNotUseGetters = true)
public class BuildPackConfiguration implements Serializable {
  private boolean publish;
  private boolean clearCache;
  private boolean trustBuilder;
  private String dockerHost;
  private String cacheImage;
  private String cache;
  private String path;
  private String registry;
  private String network;
  private String pullPolicy;
  private String descriptor;
  private String defaultProcess;
  private String lifecycleImage;
  @Singular("putEnv")
  private Map<String, String> env;
  @Singular
  private List<String> envFiles;
  @Singular
  private List<String> buildpacks;
  @Singular
  private List<String> extensions;
  @Singular
  private List<String> volumes;
  @Singular
  private List<String> tags;
  private String workspace;
  private int gid;
  private String previousImage;
  private String sbomOutputDir;
  private String reportOutputDir;
  private String creationTime;
  @Singular
  private List<String> preBuildpacks;
  @Singular
  private List<String> postBuildpacks;
  private String builderImage;
  private String runImage;


  public static BuildPackConfiguration mergeBuildPackConfigurationWithImageConfiguration(BuildPackConfiguration providedBuildPackConfig, String imagePullPolicy, List<String> tags, Map<String, String> env, List<String> volumes) {
    BuildPackConfiguration.BuildPackConfigurationBuilder buildpackConfigurationBuilder = providedBuildPackConfig.toBuilder();
    if (StringUtils.isBlank(providedBuildPackConfig.getPullPolicy()) && StringUtils.isNotBlank(imagePullPolicy)) {
      if (imagePullPolicy.equalsIgnoreCase("IfNotPresent")) {
        buildpackConfigurationBuilder.pullPolicy("if-not-present");
      }
      buildpackConfigurationBuilder.pullPolicy(imagePullPolicy.toLowerCase(Locale.ROOT));
    }
    if (tags != null) {
      if (providedBuildPackConfig.getTags() == null) {
        buildpackConfigurationBuilder.tags(tags);
      } else {
        tags.forEach(buildpackConfigurationBuilder::tag);
      }
    }
    if (env != null) {
      if (providedBuildPackConfig.getEnv() == null) {
        buildpackConfigurationBuilder.env(env);
      } else {
        env.forEach(buildpackConfigurationBuilder::putEnv);
      }
    }
    if (volumes != null) {
      if (providedBuildPackConfig.getVolumes() == null) {
        buildpackConfigurationBuilder.volumes(volumes);
      } else {
        volumes.forEach(buildpackConfigurationBuilder::volume);
      }
    }
    if (StringUtils.isBlank(providedBuildPackConfig.getCreationTime())) {
      buildpackConfigurationBuilder.creationTime("now");
    }
    return buildpackConfigurationBuilder.build();
  }

  public static BuildPackConfiguration createOpinionatedBuildPackConfiguration() {
    return BuildPackConfiguration.builder()
        .builderImage("paketobuildpacks/builder:base")
        .runImage("paketobuildpacks/run:base-cnb")
        .creationTime("now")
        .cache("type=build;format=volume;name=jkube-buildpacks-cache.build")
        .build();
  }
}
