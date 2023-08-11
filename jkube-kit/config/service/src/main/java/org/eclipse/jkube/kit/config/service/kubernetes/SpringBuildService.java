package org.eclipse.jkube.kit.config.service.kubernetes;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.Objects;
import java.util.Optional;

import org.eclipse.jkube.kit.build.service.docker.DockerServiceHub;
import org.eclipse.jkube.kit.build.service.docker.helper.ImageNameFormatter;
import org.eclipse.jkube.kit.common.ExternalCommand;
import org.eclipse.jkube.kit.common.JKubeConfiguration;
import org.eclipse.jkube.kit.common.KitLogger;
import org.eclipse.jkube.kit.common.RegistryConfig;
import org.eclipse.jkube.kit.common.util.JKubeProjectUtil;
import org.eclipse.jkube.kit.config.image.ImageConfiguration;
import org.eclipse.jkube.kit.config.image.build.JKubeBuildStrategy;
import org.eclipse.jkube.kit.config.resource.RuntimeMode;
import org.eclipse.jkube.kit.config.service.AbstractImageBuildService;
import org.eclipse.jkube.kit.config.service.BuildServiceConfig;
import org.eclipse.jkube.kit.config.service.JKubeServiceException;
import org.eclipse.jkube.kit.config.service.JKubeServiceHub;

import static org.eclipse.jkube.kit.common.util.PropertiesUtil.getValueFromProperties;
import static org.eclipse.jkube.kit.common.util.SpringBootConfigurationHelper.SPRING_BOOT_GRADLE_PLUGIN_ARTIFACT_ID;
import static org.eclipse.jkube.kit.common.util.SpringBootConfigurationHelper.SPRING_BOOT_GROUP_ID;
import static org.eclipse.jkube.kit.common.util.SpringBootUtil.isSpringBootBuildImageSupported;

public class SpringBuildService extends AbstractImageBuildService {
  private static final String SPRING_BOOT_BUILD_IMAGE_GOAL = "org.springframework.boot:spring-boot-maven-plugin:build-image";
  private static final String SPRING_BOOT_BUILD_IMAGE_TASK = "bootBuildImage";

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
    ImageNameFormatter imageNameFormatter = new ImageNameFormatter(jKubeConfiguration.getProject(), new Date());
    String defaultName = imageNameFormatter.format(Optional.ofNullable(getValueFromProperties(jKubeConfiguration.getProperties(),
        "jkube.image.name", "jkube.generator.name")).orElse("%g/%a:%l"));
    if (JKubeProjectUtil.hasPlugin(jKubeConfiguration.getProject(), SPRING_BOOT_GROUP_ID, SPRING_BOOT_GRADLE_PLUGIN_ARTIFACT_ID)) {
      executeGradleSpringBootBuildImageTask(defaultName);
    } else {
      executeMavenSpringBootBuildImageTask(defaultName);
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
    return runtimeMode.equals(RuntimeMode.KUBERNETES) &&
        buildServiceConfig.getJKubeBuildStrategy().equals(JKubeBuildStrategy.spring) &&
        isSpringBootBuildImageSupported(jKubeConfiguration.getProject());
  }

  @Override
  public void postProcess() { }

  private void executeMavenSpringBootBuildImageTask(String imageName) {
    MavenSpringBootBuildImageTaskCommand mavenSpringBootBuildImageTaskCommand = new MavenSpringBootBuildImageTaskCommand(kitLogger, imageName);
    try {
      mavenSpringBootBuildImageTaskCommand.execute();
    } catch (IOException e) {
      throw new IllegalStateException("Failure in executing Spring Boot MAVEN Plugin " + SPRING_BOOT_BUILD_IMAGE_GOAL, e);
    }
  }

  private void executeGradleSpringBootBuildImageTask(String imageName) {
    GradleSpringBootBuildImageTaskCommand gradleSpringBootBuildImageTaskCommand = new GradleSpringBootBuildImageTaskCommand(kitLogger, imageName);
    try {
      gradleSpringBootBuildImageTaskCommand.execute();
    } catch (IOException e) {
      throw new IllegalStateException("Failure in executing Spring Boot Gradle Plugin " + SPRING_BOOT_BUILD_IMAGE_TASK, e);
    }
  }

  private static class MavenSpringBootBuildImageTaskCommand extends ExternalCommand {
    private final String imageName;

    protected MavenSpringBootBuildImageTaskCommand(KitLogger log, String customImageName) {
      super(log);
      this.imageName = customImageName;
    }

    @Override
    protected String[] getArgs() {
      return new String[] { getMavenBinary(), SPRING_BOOT_BUILD_IMAGE_GOAL, "-Dspring-boot.build-image.imageName=" + imageName};
    }

    @Override
    protected void processLine(String line) {
      log.info("[[s]]%s", line);
    }

    private String getMavenBinary() {
      File localGradleWrapper = new File("./mvnw");
      if (localGradleWrapper.exists()) {
        return "./mvnw";
      }
      return "mvn";
    }
  }

  private static class GradleSpringBootBuildImageTaskCommand extends ExternalCommand {
    private final String imageName;

    protected GradleSpringBootBuildImageTaskCommand(KitLogger log, String customImageName) {
      super(log);
      this.imageName = customImageName;
    }

    @Override
    protected String[] getArgs() {
      return new String[] { getGradleBinary(), SPRING_BOOT_BUILD_IMAGE_TASK, "--imageName=" + imageName};
    }

    @Override
    protected void processLine(String line) {
      log.info("[[s]]%s", line);
    }

    private String getGradleBinary() {
      File localGradleWrapper = new File("./gradlew");
      if (localGradleWrapper.exists()) {
        return "./gradlew";
      }
      return "gradle";
    }
  }
}
