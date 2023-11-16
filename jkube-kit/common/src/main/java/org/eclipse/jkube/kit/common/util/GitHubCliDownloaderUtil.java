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
package org.eclipse.jkube.kit.common.util;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.eclipse.jkube.kit.common.KitLogger;

import java.io.File;
import java.io.IOException;
import java.net.URL;

import static org.eclipse.jkube.kit.common.archive.ArchiveDecompressor.extractArchive;

public class GitHubCliDownloaderUtil {
  private GitHubCliDownloaderUtil() { }

  public static String downloadCLIFromGitHub(KitLogger kitLogger, String baseDownloadUrl, String binaryPrefix, String artifactName, File outputDirectory) throws IOException {
    URL downloadUrl = new URL(String.format("%s/%s", baseDownloadUrl, artifactName));
    File archiveDownloadDir = new File(outputDirectory, artifactName);
    File targetExtractionDir = outputDirectory.toPath().resolve(artifactName.substring(0, artifactName.length() - 4)).toFile();

    IoUtil.download(kitLogger, downloadUrl, archiveDownloadDir);

    extractArchive(archiveDownloadDir, targetExtractionDir);
    return getCliBinaryPathFromExtractedDir(targetExtractionDir, binaryPrefix);
  }

  private static String getCliBinaryPathFromExtractedDir(File targetExtractionDir, String binaryPrefix) {
    String cliPath = null;
    File[] cliArtifactList = targetExtractionDir.listFiles(f -> f.getName().startsWith(binaryPrefix));
    if (cliArtifactList != null && cliArtifactList.length >= 1) {
      boolean wasSetExecutable = cliArtifactList[0].setExecutable(true);
      if (!wasSetExecutable) {
        throw new IllegalStateException("Unable to add executable permissions to downloaded CLI " + binaryPrefix);
      }
      cliPath = cliArtifactList[0].getAbsolutePath();
    }
    return cliPath;
  }


  public static String getCLIDownloadPlatform() {
    if (SystemUtils.IS_OS_LINUX) {
      return "linux";
    } else if (SystemUtils.IS_OS_MAC) {
      return "macos";
    }
    return "windows";
  }

  public static boolean isCLIDownloadPlatformWindows() {
    return SystemUtils.IS_OS_WINDOWS;
  }

  public static String getCLIDownloadPlatformApplicableBinary(String binaryName) {
    return isCLIDownloadPlatformWindows() ? binaryName + ".exe" : binaryName;
  }

  public static boolean isCLIDownloadPlatformProcessorArchitectureARM() {
    String architecture = System.getProperty("os.arch");
    return StringUtils.isNotBlank(architecture) && architecture.contains("aarch");
  }
}
