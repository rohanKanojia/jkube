package org.eclipse.jkube.kit.config.service.kubernetes;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;

import org.eclipse.jkube.kit.build.service.docker.DockerServiceHub;
import org.eclipse.jkube.kit.common.JKubeConfiguration;
import org.eclipse.jkube.kit.common.KitLogger;
import org.eclipse.jkube.kit.common.RegistryConfig;
import org.eclipse.jkube.kit.config.image.ImageConfiguration;
import org.eclipse.jkube.kit.config.image.build.JKubeBuildStrategy;
import org.eclipse.jkube.kit.config.resource.RuntimeMode;
import org.eclipse.jkube.kit.config.service.AbstractImageBuildService;
import org.eclipse.jkube.kit.config.service.BuildServiceConfig;
import org.eclipse.jkube.kit.config.service.JKubeServiceException;
import org.eclipse.jkube.kit.config.service.JKubeServiceHub;

import static org.eclipse.jkube.kit.common.util.SpringBootUtil.isSpringBootBuildImageSupported;

public class SpringBuildService  extends AbstractImageBuildService {
  private static final String SPRING_BOOT_BUILD_IMAGE_GOAL = "org.springframework.boot:spring-boot-maven-plugin:build-image";

  private final RuntimeMode runtimeMode;
  private final JKubeConfiguration jKubeConfiguration;
  private final DockerServiceHub dockerServices;
  private final BuildServiceConfig buildServiceConfig;
  private final KitLogger kitLogger;

  public SpringBuildService(JKubeServiceHub jKubeServiceHub) {
    super(jKubeServiceHub);
    this.runtimeMode = jKubeServiceHub.getRuntimeMode();
    this.jKubeConfiguration = Objects.requireNonNull(jKubeServiceHub.getConfiguration(),
        "JKubeConfiguration is required");
    this.dockerServices = Objects.requireNonNull(jKubeServiceHub.getDockerServiceHub(),
        "Docker Service Hub is required");
    this.buildServiceConfig = Objects.requireNonNull(jKubeServiceHub.getBuildServiceConfig(),
        "BuildServiceConfig is required");
    this.kitLogger = Objects.requireNonNull(jKubeServiceHub.getLog());
  }

  @Override
  protected void buildSingleImage(ImageConfiguration imageConfiguration) throws JKubeServiceException {
    kitLogger.info("Delegating container image building process to Spring Boot");
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
    return runtimeMode.equals(RuntimeMode.KUBERNETES) &&
        buildServiceConfig.getJKubeBuildStrategy().equals(JKubeBuildStrategy.spring) &&
        isSpringBootBuildImageSupported(jKubeConfiguration.getProject());
  }

  @Override
  public void postProcess() {
    Optional.ofNullable(jKubeConfiguration.getPostGoalTask()).ifPresent(t -> t.apply(SPRING_BOOT_BUILD_IMAGE_GOAL));
  }
}
