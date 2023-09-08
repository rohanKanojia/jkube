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

import java.io.File;
import java.io.IOException;

import org.eclipse.jkube.kit.common.KitLogger;
import org.eclipse.jkube.kit.common.util.FileUtil;
import org.eclipse.jkube.kit.config.image.build.BuildPackConfiguration;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;

import static org.eclipse.jkube.kit.common.util.GitHubCliDownloaderUtil.downloadCLIFromGitHub;
import static org.eclipse.jkube.kit.common.util.GitHubCliDownloaderUtil.getApplicableBinary;
import static org.eclipse.jkube.kit.common.util.GitHubCliDownloaderUtil.getDownloadPlatform;

public class BuildPackBuildUtil {
  private static final String DEFAULT_PACK_CLI_VERSION = "v0.31.0";
  private static final String PACK_CLI_DOWNLOADS_LINK = "https://github.com/buildpacks/pack/releases/download/%s";
  private static final String PACK_CLI_ARTIFACT_PREFIX = "pack";
  private static final String PACK_UNIX_TAR_SUFFIX = ".tgz";
  private static final String PACK_WINDOWS_ZIP_SUFFIX = ".zip";

  private BuildPackBuildUtil() { }

  public static void buildImage(KitLogger kitLogger, File baseDirectory, String outputDirectory, String imageName, BuildPackConfiguration buildPackConfiguration) {
    String cliPath = getPackCLIIfPresentOrDownload(kitLogger, baseDirectory, new File(baseDirectory, outputDirectory));
    executeBuildPacksCLIBuildImageTask(kitLogger, cliPath, imageName, buildPackConfiguration);
  }

  private static void executeBuildPacksCLIBuildImageTask(KitLogger kitLogger, String cliPath, String imageName, BuildPackConfiguration buildPackConfiguration) {
    BuildPackBuildImageCommand buildPackBuildImageCommand = new BuildPackBuildImageCommand(kitLogger, imageName, cliPath, buildPackConfiguration);
    try {
      kitLogger.info("Using pack CLI %s", cliPath);
      buildPackBuildImageCommand.execute();
    } catch (IOException e) {
      throw new IllegalStateException("Failure in executing pack command", e);
    }
  }

  private static String getPackCLIIfPresentOrDownload(KitLogger kitLogger, File baseDir, File outputDirectory) {
    try {
      String packCliDownloadBaseUrl = String.format(PACK_CLI_DOWNLOADS_LINK, DEFAULT_PACK_CLI_VERSION);
      String packCliArtifactSuffix = SystemUtils.IS_OS_WINDOWS ? PACK_WINDOWS_ZIP_SUFFIX : PACK_UNIX_TAR_SUFFIX;
      String downloadedPackCliPath = getExpectedPackCliDownloadedPath(outputDirectory);
      if (StringUtils.isBlank(downloadedPackCliPath)) {
        kitLogger.info("Downloading pack CLI %s", DEFAULT_PACK_CLI_VERSION);
        downloadedPackCliPath = downloadCLIFromGitHub(kitLogger, packCliDownloadBaseUrl, getDownloadPlatform(), PACK_CLI_ARTIFACT_PREFIX, DEFAULT_PACK_CLI_VERSION, packCliArtifactSuffix, outputDirectory);
      }
      return FileUtil.getRelativeFilePath(baseDir.getAbsolutePath(), downloadedPackCliPath);
    } catch (IOException ioException) {
      kitLogger.info("Not able to download pack CLI : " + ioException.getMessage());
      kitLogger.info("Checking for local pack CLI");
      String packCliFoundLocally = checkPackCLIPresentOnMachine(kitLogger);
      if (StringUtils.isNotBlank(packCliFoundLocally)) {
        return packCliFoundLocally;
      }
      throw new IllegalStateException("No local pack binary found, Not able to download pack CLI : " + ioException.getMessage());
    }
  }

  private static String getExpectedPackCliDownloadedPath(File outputDirectory) {
    if (outputDirectory != null && outputDirectory.exists()) {
      File extractedPackDir = new File(outputDirectory, String.format("pack-%s-%s", DEFAULT_PACK_CLI_VERSION, getDownloadPlatform()));
      if (extractedPackDir.exists()) {
        File[] foundPackCliList = extractedPackDir.listFiles(f -> f.getName().startsWith("pack") && f.canExecute());
        if (foundPackCliList != null && foundPackCliList.length > 0) {
          return foundPackCliList[0].getAbsolutePath();
        }
      }
    }
    return null;
  }

  private static String checkPackCLIPresentOnMachine(KitLogger kitLogger) {
    String packCLIVersion = getLocalPackCLIVersion(kitLogger);
    if (StringUtils.isNotBlank(packCLIVersion)) {
      return getApplicableBinary(PACK_CLI_ARTIFACT_PREFIX);
    }
    return null;
  }

  private static String getLocalPackCLIVersion(KitLogger kitLogger) {
    BuildPackVersionCommand versionCommand = new BuildPackVersionCommand(kitLogger);
    try {
      versionCommand.execute();
      return versionCommand.getVersion();
    } catch (IOException e) {
      kitLogger.info("pack CLI not found");
    }
    return null;
  }
}
