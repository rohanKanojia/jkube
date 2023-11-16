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
import org.eclipse.jkube.kit.common.KitLogger;
import org.eclipse.jkube.kit.common.util.FileUtil;
import org.eclipse.jkube.kit.common.util.PropertiesUtil;

import java.io.File;
import java.io.IOException;
import java.util.Properties;

import static org.eclipse.jkube.kit.common.util.GitHubCliDownloaderUtil.downloadCLIFromGitHub;
import static org.eclipse.jkube.kit.common.util.GitHubCliDownloaderUtil.getCLIDownloadPlatformApplicableBinary;
import static org.eclipse.jkube.kit.common.util.GitHubCliDownloaderUtil.getCLIDownloadPlatform;
import static org.eclipse.jkube.kit.common.util.GitHubCliDownloaderUtil.isCLIDownloadPlatformWindows;
import static org.eclipse.jkube.kit.common.util.GitHubCliDownloaderUtil.isCLIDownloadPlatformProcessorArchitectureARM;

public class BuildPackCliDownloader {
  private static final String DEFAULT_CLI_PROPERTIES = "/META-INF/jkube/default-cli.properties";
  private static final String PACK_DEFAULT_CLI_VERSION_PROPERTY = "pack-cli.upstream.version";
  private static final String PACK_CLI_DOWNLOADS_LINK_PROPERTY = "pack-cli.upstream.download-link";
  private static final String PACK_CLI_BINARY_PREFIX_PROPERTY = "pack-cli.upstream.binary-prefix";
  private static final String PACK_CLI_UNIX_ARCHIVE_SUFFIX_PROPERTY = "pack-cli.upstream.unix.archive-suffix";
  private static final String PACK_CLI_WINDOWS_ARCHIVE_SUFFIX_PROPERTY = "pack-cli.upstream.windows.archive-suffix";

  private final KitLogger kitLogger;
  private final String packCliVersion;
  private final String packCliDownloadLink;
  private final String packCliBinaryPrefix;
  private final String packCliUnixDownloadArchiveSuffix;
  private final String packCliWindowsDownloadArchiveSuffix;

  public BuildPackCliDownloader(KitLogger kitLogger) {
    this.kitLogger = kitLogger;
    Properties properties = PropertiesUtil.getPropertiesFromResource(getClass().getResource(DEFAULT_CLI_PROPERTIES));
    packCliVersion = (String) properties.get(PACK_DEFAULT_CLI_VERSION_PROPERTY);
    packCliDownloadLink = (String) properties.get(PACK_CLI_DOWNLOADS_LINK_PROPERTY);
    packCliBinaryPrefix = (String) properties.get(PACK_CLI_BINARY_PREFIX_PROPERTY);
    packCliUnixDownloadArchiveSuffix = (String) properties.get(PACK_CLI_UNIX_ARCHIVE_SUFFIX_PROPERTY);
    packCliWindowsDownloadArchiveSuffix = (String) properties.get(PACK_CLI_WINDOWS_ARCHIVE_SUFFIX_PROPERTY);
  }

  public String getPackCLIIfPresentOrDownload(File baseDir, File outputDirectory) {
    try {
      String packCliDownloadBaseUrl = packCliDownloadLink + "/" + packCliVersion;
      String packCliArtifactSuffix = String.format(".%s", isCLIDownloadPlatformWindows() ? packCliWindowsDownloadArchiveSuffix : packCliUnixDownloadArchiveSuffix);
      String downloadedPackCliPath = findPreviouslyDownloadedPackCLIPath(outputDirectory);

      if (StringUtils.isBlank(downloadedPackCliPath)) {
        kitLogger.info("Downloading pack CLI %s", packCliVersion);
        String downloadArtifactName = createApplicableDownloadArtifactName(packCliBinaryPrefix, getCLIDownloadPlatform(), packCliVersion, packCliArtifactSuffix);
        downloadedPackCliPath = downloadCLIFromGitHub(kitLogger, packCliDownloadBaseUrl, packCliBinaryPrefix, downloadArtifactName, outputDirectory);
      }
      return FileUtil.getRelativeFilePath(baseDir.getAbsolutePath(), downloadedPackCliPath);
    } catch (IOException ioException) {
      kitLogger.info("Not able to download pack CLI : " + ioException.getMessage());
      return getLocalPackCLI();
    }
  }

  public String createApplicableDownloadArtifactName(String artifactNamePrefix, String platform, String version, String suffix) {
    StringBuilder artifactNameBuilder = new StringBuilder();
    artifactNameBuilder.append(artifactNamePrefix).append("-").append(version).append("-").append(platform);
    if (isCLIDownloadPlatformProcessorArchitectureARM()) {
      artifactNameBuilder.append("-arm64");
    }
    artifactNameBuilder.append(suffix);
    return artifactNameBuilder.toString();
  }

  private String getLocalPackCLI() {
    kitLogger.info("Checking for local pack CLI");
    String packCliFound = checkPackCLIPresentOnMachine();
    if (StringUtils.isBlank(packCliFound)) {
      throw new IllegalStateException("No local pack binary found");
    }
    return packCliFound;
  }

  private String findPreviouslyDownloadedPackCLIPath(File outputDirectory) {
    if (outputDirectory != null && outputDirectory.exists()) {
      File extractedPackDir = new File(outputDirectory, String.format("pack-%s-%s", packCliVersion, getCLIDownloadPlatform()));
      if (extractedPackDir.exists()) {
        File[] foundPackCliList = extractedPackDir.listFiles(f -> f.getName().startsWith("pack") && f.canExecute());
        if (foundPackCliList != null && foundPackCliList.length > 0 &&
            isPackCLIValid(foundPackCliList[0].getAbsolutePath())) {
          return foundPackCliList[0].getAbsolutePath();
        }
      }
    }
    return null;
  }

  private String checkPackCLIPresentOnMachine() {
    String localPackCliPath = getCLIDownloadPlatformApplicableBinary(packCliBinaryPrefix);
    if (isPackCLIValid(localPackCliPath)) {
      return getCLIDownloadPlatformApplicableBinary(packCliBinaryPrefix);
    }
    return null;
  }

  private boolean isPackCLIValid(String packCLIPath) {
    BuildPackVersionCommand versionCommand = new BuildPackVersionCommand(kitLogger, packCLIPath);
    try {
      versionCommand.execute();
      return true;
    } catch (IOException e) {
      return false;
    }
  }
}
