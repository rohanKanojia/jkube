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
package org.eclipse.jkube.gradle.plugin.task;

import java.io.IOException;
import java.util.Date;
import java.util.Optional;

import javax.inject.Inject;

import org.eclipse.jkube.gradle.plugin.GradleUtil;
import org.eclipse.jkube.gradle.plugin.KubernetesExtension;
import org.eclipse.jkube.kit.build.service.docker.DockerServiceHub;
import org.eclipse.jkube.kit.build.service.docker.helper.ImageNameFormatter;
import org.eclipse.jkube.kit.common.util.SpringBootUtil;
import org.eclipse.jkube.kit.config.image.ImageConfiguration;
import org.eclipse.jkube.kit.config.image.build.JKubeBuildStrategy;
import org.eclipse.jkube.kit.config.resource.RuntimeMode;
import org.eclipse.jkube.kit.config.service.BuildServiceConfig;
import org.eclipse.jkube.kit.config.service.JKubeServiceException;
import org.eclipse.jkube.kit.config.service.JKubeServiceHub;

import org.gradle.api.GradleException;
import org.gradle.api.Project;

import static org.eclipse.jkube.kit.common.util.BuildReferenceDateUtil.getBuildTimestamp;
import static org.eclipse.jkube.kit.common.util.BuildReferenceDateUtil.getBuildTimestampFile;
import static org.eclipse.jkube.kit.common.util.EnvUtil.storeTimestamp;
import static org.eclipse.jkube.kit.common.util.PropertiesUtil.getValueFromProperties;

@SuppressWarnings("CdiInjectionPointsInspection")
public class KubernetesBuildTask extends AbstractJKubeTask {

  @Inject
  public KubernetesBuildTask(Class<? extends KubernetesExtension> extensionClass) {
    super(extensionClass);
    setDescription(
        "Builds the container images configured for this project via a Docker, S2I binary build or any of the other available build strategies.");
    addSpringBootBuildImageFinalizerIfApplicable(getProject());
  }

  @Override
  protected JKubeServiceHub.JKubeServiceHubBuilder initJKubeServiceHubBuilder() {
    return super.initJKubeServiceHubBuilder()
        .dockerServiceHub(DockerServiceHub.newInstance(kitLogger, TaskUtil.initDockerAccess(kubernetesExtension, kitLogger),
            logOutputSpecFactory))
        .buildServiceConfig(buildServiceConfigBuilder().build());
  }

  @Override
  public void run() {
    if (kubernetesExtension.getRuntimeMode() == RuntimeMode.OPENSHIFT) {
      kitLogger.info("Using [[B]]OpenShift[[B]] build with strategy [[B]]%s[[B]]",
          kubernetesExtension.getBuildStrategyOrDefault().getLabel());
    } else {
      kitLogger.info("Building container image in [[B]]Kubernetes[[B]] mode");
    }
    try {
      storeTimestamp(
          getBuildTimestampFile(kubernetesExtension.javaProject.getBuildDirectory().getAbsolutePath(),
              DOCKER_BUILD_TIMESTAMP),
          getBuildTimestamp(null, null, kubernetesExtension.javaProject.getBuildDirectory().getAbsolutePath(),
              DOCKER_BUILD_TIMESTAMP));
      jKubeServiceHub.getBuildService().build(resolvedImages.toArray(new ImageConfiguration[0]));
    } catch (JKubeServiceException | IOException e) {
      kitLogger.error(e.getMessage());
      throw new GradleException(e.getMessage(), e);
    }
  }

  @Override
  protected boolean shouldSkip() {
    return super.shouldSkip() || kubernetesExtension.getSkipBuildOrDefault();
  }

  protected BuildServiceConfig.BuildServiceConfigBuilder buildServiceConfigBuilder() {
    return TaskUtil.buildServiceConfigBuilder(kubernetesExtension);
  }

  private void addSpringBootBuildImageFinalizerIfApplicable(Project gradleProject) {
    kubernetesExtension.javaProject = GradleUtil.convertGradleProject(gradleProject);
    if (kubernetesExtension.getBuildStrategyOrDefault().equals(JKubeBuildStrategy.spring) &&
        SpringBootUtil.isSpringBootBuildImageSupported(kubernetesExtension.javaProject)) {
      ImageNameFormatter imageNameFormatter = new ImageNameFormatter(kubernetesExtension.javaProject, new Date());
      String defaultName = imageNameFormatter.format(Optional.ofNullable(getValueFromProperties(kubernetesExtension.javaProject.getProperties(),
          "jkube.image.name", "jkube.generator.name")).orElse("%g/%a:%l"));
      GradleUtil.finalizeWithGradlePluginTask(getProject(), this, "bootBuildImage");
      GradleUtil.setPropertyIfNotPresentInTask(getProject(), "bootBuildImage", "imageName", defaultName);
    }
  }
}
