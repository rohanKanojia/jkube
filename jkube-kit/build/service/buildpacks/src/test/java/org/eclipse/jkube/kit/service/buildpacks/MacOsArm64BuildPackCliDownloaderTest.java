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

public class MacOsArm64BuildPackCliDownloaderTest extends AbstractBuildPackCliDownloaderTest {
  @Override
  String getPlatform() {
    return "macos";
  }

  @Override
  String getPlatformBinary() {
    return "pack";
  }

  @Override
  String getExpectedDownloadArchiveName() {
    return "pack-v0.32.1-macos-arm64.tgz";
  }

  @Override
  boolean isPlatformARMProcessorArchitecture() {
    return true;
  }
}
