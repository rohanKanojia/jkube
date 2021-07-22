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

import javax.inject.Inject;

import org.eclipse.jkube.gradle.plugin.KubernetesExtension;
import org.eclipse.jkube.kit.build.api.helper.DockerFileUtil;
import org.eclipse.jkube.kit.build.service.docker.DockerAccessFactory;
import org.eclipse.jkube.kit.build.service.docker.ImagePullManager;
import org.eclipse.jkube.kit.build.service.docker.ServiceHubFactory;
import org.eclipse.jkube.kit.build.service.docker.access.DockerAccess;
import org.eclipse.jkube.kit.build.service.docker.access.log.LogOutputSpecFactory;
import org.eclipse.jkube.kit.build.service.docker.helper.ConfigHelper;
import org.eclipse.jkube.kit.build.service.docker.helper.ImageNameFormatter;
import org.eclipse.jkube.kit.common.util.EnvUtil;
import org.eclipse.jkube.kit.config.image.ImageConfiguration;
import org.eclipse.jkube.kit.config.image.build.JKubeBuildStrategy;
import org.eclipse.jkube.kit.config.resource.BuildRecreateMode;
import org.eclipse.jkube.kit.config.service.BuildServiceConfig;
import org.eclipse.jkube.kit.config.service.JKubeServiceException;
import org.eclipse.jkube.kit.config.service.JKubeServiceHub;
import org.gradle.api.tasks.Internal;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Date;
import java.util.Properties;

import static org.eclipse.jkube.kit.common.util.PropertiesUtil.getValueFromProperties;

@SuppressWarnings("CdiInjectionPointsInspection")
public class KubernetesBuildTask extends AbstractJKubeTask {

  @Inject
  public KubernetesBuildTask(Class<? extends KubernetesExtension> extensionClass) {
    super(extensionClass);
    setDescription("Builds the container images configured for this project via a Docker, S2I binary build or any of the other available build strategies.");
  }

  @Override
  protected JKubeServiceHub.JKubeServiceHubBuilder initJKubeServiceHubBuilder() {
    JKubeServiceHub.JKubeServiceHubBuilder builder = super.initJKubeServiceHubBuilder();
    DockerAccessFactory.DockerAccessContext dockerAccessContext = getDockerAccessContext();
    DockerAccessFactory dockerAccessFactory = new DockerAccessFactory();
    DockerAccess access = dockerAccessFactory.createDockerAccess(dockerAccessContext);
    LogOutputSpecFactory logSpecFactory = new LogOutputSpecFactory(true, true, null);
    ServiceHubFactory serviceHubFactory = new ServiceHubFactory();
    builder.dockerServiceHub(serviceHubFactory.createServiceHub(access, kitLogger, logSpecFactory));
    builder.buildServiceConfig(buildServiceConfigBuilder().build());

    return builder;
  }

  @Override
  public void run() {
    try {
      if (DockerFileUtil.isSimpleDockerFileMode(javaProject.getBaseDirectory())) {
        kitLogger.info("Simple DockerFile mode detected");
        File dockerFile = new File(javaProject.getBaseDirectory(), "Dockerfile");
        ImageNameFormatter imageNameFormatter = new ImageNameFormatter(javaProject, getBuildTimestamp());
        String defaultImageName = imageNameFormatter.format(getValueFromProperties(javaProject.getProperties(),
                "jkube.image.name", "jkube.generator.name"));
        ImageConfiguration imageConfiguration = DockerFileUtil.createSimpleDockerfileConfig(dockerFile, defaultImageName);
        ConfigHelper.initAndValidate(Collections.singletonList(imageConfiguration), null, imageNameFormatter);
        jKubeServiceHub.getBuildService().build(imageConfiguration);
      }
    } catch (JKubeServiceException | IOException e) {
      kitLogger.error(e.getMessage());
    }
  }

  @Internal
  protected DockerAccessFactory.DockerAccessContext getDockerAccessContext() {
    return DockerAccessFactory.DockerAccessContext.builder()
            .projectProperties(javaProject.getProperties())
            .log(kitLogger)
            .maxConnections(10)
            .build();
  }

  protected BuildServiceConfig.BuildServiceConfigBuilder buildServiceConfigBuilder() {
    return BuildServiceConfig.builder()
            .buildRecreateMode(BuildRecreateMode.fromParameter("none"))
            .jKubeBuildStrategy(JKubeBuildStrategy.docker)
            .forcePull(true)
            .imagePullManager(getImagePullManager(null, null))
            .buildDirectory(javaProject.getBuildDirectory().getPath());
  }

  public ImagePullManager getImagePullManager(String imagePullPolicy, String autoPull) {
    return new ImagePullManager(getSessionCacheStore(), imagePullPolicy, autoPull);
  }

  @Internal
  protected synchronized Date getBuildTimestamp() throws IOException {
    return getReferenceDate();
  }

  // Get the reference date for the build. By default this is picked up
  // from an existing build date file. If this does not exist, the current date is used.
  @Internal
  protected Date getReferenceDate() throws IOException {
    Date referenceDate = EnvUtil.loadTimestamp(getBuildTimestampFile());
    return referenceDate != null ? referenceDate : new Date();
  }

  // used for storing a timestamp
  @Internal
  protected File getBuildTimestampFile() {
    return new File(javaProject.getBuildDirectory(), DOCKER_BUILD_TIMESTAMP);
  }

  @Internal
  protected ImagePullManager.CacheStore getSessionCacheStore() {
    return new ImagePullManager.CacheStore() {
      @Override
      public String get(String key) {
        Properties userProperties = javaProject.getProperties();
        return userProperties.getProperty(key);
      }

      @Override
      public void put(String key, String value) {
        Properties userProperties = javaProject.getProperties();
        userProperties.setProperty(key, value);
      }
    };
  }
}
