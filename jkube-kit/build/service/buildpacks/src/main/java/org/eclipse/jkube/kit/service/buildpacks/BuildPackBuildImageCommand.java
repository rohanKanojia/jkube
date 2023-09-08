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

import org.apache.commons.lang3.StringUtils;
import org.eclipse.jkube.kit.common.ExternalCommand;
import org.eclipse.jkube.kit.common.KitLogger;
import org.eclipse.jkube.kit.config.image.ImageName;
import org.eclipse.jkube.kit.config.image.build.BuildPackConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class BuildPackBuildImageCommand extends ExternalCommand {
  private final String imageName;
  private final KitLogger logger;
  private final String cliPath;
  private final BuildPackConfiguration buildpackConfiguration;

  protected BuildPackBuildImageCommand(KitLogger log, String imageName, String cliPath, BuildPackConfiguration buildpackConfiguration) {
    super(log);
    this.logger = log;
    this.imageName = imageName;
    this.cliPath = cliPath;
    this.buildpackConfiguration = buildpackConfiguration;
  }

  @Override
  protected String[] getArgs() {
    List<String> args = new ArrayList<>();
    args.add(cliPath);
    args.add("build");
    args.add(imageName);
    args.addAll(extractStringArg("--builder", buildpackConfiguration.getBuilderImage()));
    args.addAll(extractStringArg("--buildpack-registry", buildpackConfiguration.getRegistry()));
    args.addAll(extractStringArg("--cache", buildpackConfiguration.getCache()));
    args.addAll(extractStringArg("--cache-image", buildpackConfiguration.getCacheImage()));
    args.addAll(extractStringArg("--creation-time", buildpackConfiguration.getCreationTime()));
    args.addAll(extractStringArg("--default-process", buildpackConfiguration.getDefaultProcess()));
    args.addAll(extractStringArg("--descriptor", buildpackConfiguration.getDescriptor()));
    args.addAll(extractStringArg("--docker-host", buildpackConfiguration.getDockerHost()));
    args.addAll(extractStringArg("--lifecycle-image", buildpackConfiguration.getLifecycleImage()));
    args.addAll(extractStringArg("--network", buildpackConfiguration.getNetwork()));
    args.addAll(extractStringArg("--path", buildpackConfiguration.getPath()));
    args.addAll(extractStringArg("--previous-image", buildpackConfiguration.getPreviousImage()));
    args.addAll(extractStringArg("--pull-policy", buildpackConfiguration.getPullPolicy()));
    args.addAll(extractStringArg("--report-output-dir", buildpackConfiguration.getReportOutputDir()));
    args.addAll(extractStringArg("--run-image", buildpackConfiguration.getRunImage()));
    args.addAll(extractStringArg("--sbom-output-dir", buildpackConfiguration.getSbomOutputDir()));
    args.addAll(extractStringArg("--workspace", buildpackConfiguration.getWorkspace()));
    args.addAll(extractBooleanArg("--clear-cache", buildpackConfiguration.isClearCache()));
    args.addAll(extractBooleanArg("--publish", buildpackConfiguration.isPublish()));
    args.addAll(extractBooleanArg("--trust-builder", buildpackConfiguration.isTrustBuilder()));
    args.addAll(extractRepeatedArgsForListElements("--env-file", buildpackConfiguration.getEnvFiles()));
    args.addAll(extractRepeatedArgsForListElements("--post-buildpack", buildpackConfiguration.getPostBuildpacks()));
    args.addAll(extractRepeatedArgsForListElements("--pre-buildpack", buildpackConfiguration.getPreBuildpacks()));
    args.addAll(extractRepeatedArgsForListElements("--volume", buildpackConfiguration.getVolumes()));
    args.addAll(extractRepeatedArgsForListElements("--buildpack", buildpackConfiguration.getBuildpacks()));
    args.addAll(extractRepeatedArgsForListElements("--extension", buildpackConfiguration.getExtensions()));
    if (buildpackConfiguration.getEnv() != null) {
      List<String> keyValueEntryList = buildpackConfiguration.getEnv().entrySet().stream()
          .map(this::getSingleEnvArg)
          .collect(Collectors.toList());
      args.addAll(extractRepeatedArgsForListElements("--env", keyValueEntryList));
    }
    if (buildpackConfiguration.getGid() > 0) {
      args.add("--gid");
      args.add(String.valueOf(buildpackConfiguration.getGid()));
    }
    if (buildpackConfiguration.getTags() != null && !buildpackConfiguration.getTags().isEmpty()) {
      ImageName specifiedImageName = new ImageName(imageName);
      List<String> imageNameWithAdditionalTags = buildpackConfiguration.getTags().stream()
          .map(t -> specifiedImageName.getNameWithoutTag() + ":" + t)
          .collect(Collectors.toList());
      args.addAll(extractRepeatedArgsForListElements("--tag", imageNameWithAdditionalTags));
    }

    return args.toArray(new String[0]);
  }

  @Override
  protected void processLine(String line) {
    logger.info("[[s]]%s", line);
  }

  private List<String> extractBooleanArg(String flag, boolean flagValue) {
    List<String> args = new ArrayList<>();
    if (flagValue) {
      args.add(flag);
    }
    return args;
  }

  private List<String> extractStringArg(String flag, String flagValue) {
    List<String> args = new ArrayList<>();
    if (StringUtils.isNotBlank(flagValue)) {
      args.add(flag);
      args.add(flagValue);
    }
    return args;
  }

  private List<String> extractRepeatedArgsForListElements(String flag, List<String> flagValues) {
    List<String> args = new ArrayList<>();
    if (flagValues != null && !flagValues.isEmpty()) {
      flagValues.forEach(v -> args.addAll(extractStringArg(flag, v)));
    }
    return args;
  }

  private String getSingleEnvArg(Map.Entry<String, String> e) {
    String entryKeyValue = e.getKey();
    if (StringUtils.isNotBlank(e.getValue())) {
      entryKeyValue = entryKeyValue + "=" + e.getValue();
    }
    return entryKeyValue;
  }
}
