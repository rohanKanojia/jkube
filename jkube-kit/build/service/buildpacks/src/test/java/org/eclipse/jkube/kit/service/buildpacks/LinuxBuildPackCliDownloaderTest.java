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


@SuppressWarnings("java:S2187")
class LinuxBuildPackCliDownloaderTest extends AbstractBuildPackCliDownloaderTest {
  @Override
  String getPlatform() {
    return "linux";
  }

  @Override
  String getPlatformBinary() {
    return "pack";
  }

  @Override
  String getExpectedDownloadArchiveName() {
    return "pack-v0.32.1-linux.tgz";
  }

  @Override
  boolean isPlatformARMProcessorArchitecture() {
    return false;
  }
}
