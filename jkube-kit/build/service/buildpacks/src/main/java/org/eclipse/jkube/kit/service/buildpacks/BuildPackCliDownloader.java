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

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.util.Collections;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;

import static java.nio.file.StandardCopyOption.COPY_ATTRIBUTES;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static org.eclipse.jkube.kit.common.util.EnvUtil.findBinaryFileInUserPath;
import static org.eclipse.jkube.kit.common.util.IoUtil.downloadArchive;
import static org.eclipse.jkube.kit.common.util.EnvUtil.isMacOs;
import static org.eclipse.jkube.kit.common.util.EnvUtil.isProcessorArchitectureARM;
import static org.eclipse.jkube.kit.common.util.EnvUtil.isWindows;
import static org.eclipse.jkube.kit.common.util.EnvUtil.getUserHome;
import static org.eclipse.jkube.kit.common.util.SemanticVersionUtil.removeBuildMetadata;

public class BuildPackCliDownloader {
  private static final String JKUBE_PACK_DIR = ".jkube";
  private static final String PACK_UNIX_CLI_NAME = "unix.binary-name";
  private static final String PACK_WINDOWS_CLI_NAME = "windows.binary-name";
  private static final String PACK_DEFAULT_CLI_VERSION_PROPERTY = "version";
  private static final String PACK_CLI_LINUX_ARTIFACT = "linux.artifact";
  private static final String PACK_CLI_LINUX_ARM64_ARTIFACT = "linux-arm64.artifact";
  private static final String PACK_CLI_MACOS_ARTIFACT = "macos.artifact";
  private static final String PACK_CLI_MACOS_ARM64_ARTIFACT = "macos-arm64.artifact";
  private static final String PACK_CLI_WINDOWS_ARTIFACT = "windows.artifact";

  private final KitLogger kitLogger;
  private final String packUnixBinaryName;
  private final String packWindowsBinaryName;
  private final String packCliVersion;
  private final String packCliLinuxArtifact;
  private final String packCliLinuxArm64Artifact;
  private final String packCliMacOsArtifact;
  private final String packCliMacOsArm64Artifact;
  private final String packCliWindowsArtifact;

  public BuildPackCliDownloader(KitLogger kitLogger, Properties packProperties) {
    this.kitLogger = kitLogger;
    packUnixBinaryName = (String) packProperties.get(PACK_UNIX_CLI_NAME);
    packWindowsBinaryName = (String) packProperties.get(PACK_WINDOWS_CLI_NAME);
    packCliVersion = (String) packProperties.get(PACK_DEFAULT_CLI_VERSION_PROPERTY);
    packCliLinuxArtifact = (String) packProperties.get(PACK_CLI_LINUX_ARTIFACT);
    packCliLinuxArm64Artifact = (String) packProperties.get(PACK_CLI_LINUX_ARM64_ARTIFACT);
    packCliMacOsArtifact = (String) packProperties.get(PACK_CLI_MACOS_ARTIFACT);
    packCliMacOsArm64Artifact = (String) packProperties.get(PACK_CLI_MACOS_ARM64_ARTIFACT);
    packCliWindowsArtifact = (String) packProperties.get(PACK_CLI_WINDOWS_ARTIFACT);
  }

  public File getPackCLIIfPresentOrDownload() {
    try {
      File jKubeUserHomeDir = new File(getUserHome(), JKUBE_PACK_DIR);
      FileUtil.createDirectory(jKubeUserHomeDir);
      File pack = findPreviouslyDownloadedPackCLIPath(jKubeUserHomeDir);
      File packInJKubeUserHome = new File(jKubeUserHomeDir, getPlatformBinaryName());

      if (pack == null) {
        File tempDownloadDirectory = FileUtil.createTempDirectory();
        URL downloadUrl = new URL(inferApplicableDownloadArtifactUrl());
        File downloadedPackBinary = downloadPackCli(downloadUrl, tempDownloadDirectory);
        Files.copy(downloadedPackBinary.toPath(), packInJKubeUserHome.toPath(), REPLACE_EXISTING, COPY_ATTRIBUTES);
        FileUtil.cleanDirectory(tempDownloadDirectory);
        return new File(jKubeUserHomeDir, downloadedPackBinary.getName());
      }
      return pack;
    } catch (IOException ioException) {
      kitLogger.info("Not able to download pack CLI : " + ioException.getMessage());
      return getLocalPackCLI();
    }
  }

  private File downloadPackCli(URL downloadUrl, File packCliDownloadDir) throws IOException {
    FileUtil.cleanDirectory(packCliDownloadDir);
    kitLogger.info("Downloading pack CLI %s", packCliVersion);
    downloadArchive(downloadUrl, packCliDownloadDir);
    File pack = new File(packCliDownloadDir, getPlatformBinaryName());
    if (!pack.exists()) {
      throw new IllegalStateException("Unable to find " + getPlatformBinaryName() + " in downloaded artifact");
    }
    if (!pack.canExecute() && !pack.setExecutable(true)) {
      throw new IllegalStateException("Failure in setting execute permission in " + pack.getAbsolutePath());
    }
    return pack;
  }

  private String inferApplicableDownloadArtifactUrl() {
    if (isWindows()) {
      return packCliWindowsArtifact;
    } else if (isMacOs() && isProcessorArchitectureARM()) {
      return packCliMacOsArm64Artifact;
    } else if(isMacOs()) {
      return packCliMacOsArtifact;
    } else if (isProcessorArchitectureARM()) {
      return packCliLinuxArm64Artifact;
    }
    return packCliLinuxArtifact;
  }

  private File getLocalPackCLI() {
    kitLogger.info("Checking for local pack CLI");
    String packCliFound = checkPackCLIPresentOnMachine();
    if (StringUtils.isBlank(packCliFound)) {
      throw new IllegalStateException("No local pack binary found");
    }
    return new File(packCliFound);
  }

  private File findPreviouslyDownloadedPackCLIPath(File downloadDirectory) {
    if (downloadDirectory != null && downloadDirectory.exists()) {
      return FileUtil.listFilesAndDirsRecursivelyInDirectory(downloadDirectory)
          .stream()
          .filter(f -> f.getName().startsWith(packUnixBinaryName) && f.canExecute())
          .filter(f -> isValid(f.getAbsolutePath()))
          .findFirst()
          .orElse(null);
    }
    return null;
  }

  private String checkPackCLIPresentOnMachine() {
    File packCliFoundOnUserPath = findBinaryFileInUserPath(getPlatformBinaryName());
    if (packCliFoundOnUserPath != null && isValid(packCliFoundOnUserPath.getAbsolutePath())) {
      return packCliFoundOnUserPath.getAbsolutePath();
    }
    return null;
  }

  public boolean isValid(String packCliPath) {
    AtomicReference<String> versionRef = new AtomicReference<>();
    BuildPackCommand versionCommand = new BuildPackCommand(kitLogger, packCliPath, Collections.singletonList("--version"), versionRef::set);
    try {
      versionCommand.execute();
      String version = removeBuildMetadata(versionRef.get());
      return StringUtils.isNotBlank(version) && packCliVersion.contains(version);
    } catch (IOException e) {
      return false;
    }
  }

  private String getPlatformBinaryName() {
    return isWindows() ? packWindowsBinaryName : packUnixBinaryName;
  }
}