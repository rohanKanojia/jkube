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

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.eclipse.jkube.kit.common.KitLogger;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class GitHubCliDownloaderUtil {
  private GitHubCliDownloaderUtil() { }

  public static String downloadCLIFromGitHub(KitLogger kitLogger, String baseDownloadUrl, String platform, String artifactNamePrefix, String version, String archiveSuffix, File outputDirectory) throws IOException {
    String artifactName = getApplicableDownloadArtifact(artifactNamePrefix, platform, version, archiveSuffix);
    URL downloadUrl;
    downloadUrl = new URL(String.format("%s/%s", baseDownloadUrl, artifactName));
    File downloadedArchive = new File(outputDirectory, artifactName);
    IoUtil.download(kitLogger, downloadUrl, downloadedArchive);
    return extractDownloadedArchive(downloadedArchive, outputDirectory.toPath().resolve(artifactName.substring(0, artifactName.length() - 4)));
  }

  public static String getDownloadPlatform() {
    if (SystemUtils.IS_OS_LINUX) {
      return "linux";
    } else if (SystemUtils.IS_OS_MAC) {
      return "macos";
    }
    return "windows";
  }

  public static String getApplicableBinary(String binaryName) {
    return SystemUtils.IS_OS_WINDOWS ? binaryName + ".exe" : binaryName;
  }

  private static String extractDownloadedArchive(File downloadedArchive, Path targetExtractionDir) throws IOException {
    if (targetExtractionDir.toFile().exists()) {
      FileUtil.cleanDirectory(targetExtractionDir.toFile());
    }
    Files.createDirectory(targetExtractionDir);
    if (downloadedArchive.getName().endsWith(".tgz")) {
      try (BufferedInputStream inputStream = new BufferedInputStream(Files.newInputStream(downloadedArchive.toPath()));
           TarArchiveInputStream tar = new TarArchiveInputStream(new GzipCompressorInputStream(inputStream))) {
        ArchiveEntry entry;
        while ((entry = tar.getNextEntry()) != null) {
          Path extractTo = targetExtractionDir.resolve(entry.getName());
          if (entry.isDirectory()) {
            Files.createDirectories(extractTo);
          } else {
            Files.copy(tar, extractTo);
          }
        }
      }
    } else if (downloadedArchive.getName().endsWith(".zip")) {
      byte[] buffer = new byte[1024];
      try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(downloadedArchive.toPath()))) {
        ZipEntry zipEntry = zis.getNextEntry();
        while (zipEntry != null) {
          File newFile = new File(targetExtractionDir.toFile(), zipEntry.getName());
          if (zipEntry.isDirectory()) {
            if (!newFile.isDirectory() && !newFile.mkdirs()) {
              throw new IOException("Failed to create directory " + newFile);
            }
          } else {
            // fix for Windows-created archives
            File parent = newFile.getParentFile();
            if (!parent.isDirectory() && !parent.mkdirs()) {
              throw new IOException("Failed to create directory " + parent);
            }

            // write file content
            try (FileOutputStream fos = new FileOutputStream(newFile)) {
              int len;
              while ((len = zis.read(buffer)) > 0) {
                fos.write(buffer, 0, len);
              }
            }
          }
          zipEntry = zis.getNextEntry();
        }
        zis.closeEntry();
      }
    }
    String packCliPath = null;
    File[] packCliArtifactList = targetExtractionDir.toFile().listFiles(f -> f.getName().startsWith("pack"));
    if (packCliArtifactList != null && packCliArtifactList.length >= 1) {
      boolean wasSetExecutable = packCliArtifactList[0].setExecutable(true);
      if (!wasSetExecutable) {
        throw new IllegalStateException("Unable to add executable permissions to downloaded pack CLI");
      }
      packCliPath = packCliArtifactList[0].getAbsolutePath();
    }
    return packCliPath;
  }

  private static String getApplicableDownloadArtifact(String artifactNamePrefix, String platform, String version, String suffix) {
    StringBuilder artifactNameBuilder = new StringBuilder();
    artifactNameBuilder.append(artifactNamePrefix).append("-").append(version);
    artifactNameBuilder.append("-").append(platform);
    String architecture = System.getenv("PROCESSOR_ARCHITECTURE");
    if (StringUtils.isNotBlank(architecture) && architecture.contains("arm")) {
      artifactNameBuilder.append("-arm64");
    }
    artifactNameBuilder.append(suffix);
    return artifactNameBuilder.toString();
  }
}
