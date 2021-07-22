/**
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

import java.util.Collections;

import org.eclipse.jkube.gradle.plugin.GradleUtil;
import org.eclipse.jkube.gradle.plugin.KubernetesExtension;
import org.eclipse.jkube.kit.common.JKubeConfiguration;
import org.eclipse.jkube.kit.common.JavaProject;
import org.eclipse.jkube.kit.common.KitLogger;
import org.eclipse.jkube.kit.common.RegistryConfig;
import org.eclipse.jkube.kit.common.util.Slf4jKitLogger;
import org.eclipse.jkube.kit.config.access.ClusterAccess;
import org.eclipse.jkube.kit.config.access.ClusterConfiguration;
import org.eclipse.jkube.kit.config.image.build.RegistryAuthConfiguration;
import org.eclipse.jkube.kit.config.service.JKubeServiceHub;

import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.TaskAction;

import static org.eclipse.jkube.gradle.plugin.KubernetesExtension.DEFAULT_OFFLINE;

public abstract class AbstractJKubeTask extends DefaultTask implements JKubeTask {

  @Internal
  private final KubernetesExtension kubernetesExtension;
  @Input
  protected JavaProject javaProject;
  @Input
  protected KitLogger kitLogger;
  @Input
  protected ClusterAccess clusterAccess;
  @Input
  protected JKubeServiceHub jKubeServiceHub;
  @Input
  protected RegistryAuthConfiguration authConfig;
  @Input
  protected boolean skipExtendedAuth;
  @Input
  protected String registry;
  @Input
  protected String pullRegistry;
  @Internal
  public static final String DOCKER_BUILD_TIMESTAMP = "docker/build.timestamp";

  protected AbstractJKubeTask(Class<? extends KubernetesExtension> extensionClass) {
    kubernetesExtension = getProject().getExtensions().getByType(extensionClass);
  }

  @TaskAction
  @Input
  public final void runTask() {
    javaProject = GradleUtil.convertGradleProject(getProject());
    kitLogger = new Slf4jKitLogger(getLogger());
    clusterAccess = new ClusterAccess(kitLogger, initClusterConfiguration());
    jKubeServiceHub = initJKubeServiceHubBuilder().build();
    run();
  }

  @Input
  protected JKubeServiceHub.JKubeServiceHubBuilder initJKubeServiceHubBuilder() {
    return JKubeServiceHub.builder()
        .log(kitLogger)
        .configuration(JKubeConfiguration.builder()
            .project(javaProject)
            .reactorProjects(Collections.singletonList(javaProject))
                .sourceDirectory("src/main/jkube")
                .outputDirectory("build/docker")
                .registryConfig(getRegistryConfig(pullRegistry))
            .build())
        .clusterAccess(clusterAccess)
        .offline(kubernetesExtension.getOffline().getOrElse(DEFAULT_OFFLINE))
        .platformMode(kubernetesExtension.getRuntimeMode());
  }

  @Input
  protected ClusterConfiguration initClusterConfiguration() {
    return ClusterConfiguration.from(kubernetesExtension.access,
        System.getProperties(), javaProject.getProperties()).build();
  }

  @Input
  @Internal
  protected final JKubeServiceHub getJKubeServiceHub() {
    return jKubeServiceHub;
  }

  protected RegistryConfig getRegistryConfig(String specificRegistry) {
    return RegistryConfig.builder()
            .settings(Collections.emptyList())
            .authConfig(authConfig != null ? authConfig.toMap() : null)
            .skipExtendedAuth(skipExtendedAuth)
            .registry(specificRegistry != null ? specificRegistry : registry)
            .build();
  }
}
