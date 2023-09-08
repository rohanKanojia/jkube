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

import lombok.Getter;
import org.eclipse.jkube.kit.common.ExternalCommand;
import org.eclipse.jkube.kit.common.KitLogger;

import static org.eclipse.jkube.kit.common.util.GitHubCliDownloaderUtil.getApplicableBinary;

@Getter
public class BuildPackVersionCommand extends ExternalCommand {
  private String version;
  private static final String PACK_CLI_ARTIFACT_PREFIX = "pack";

  protected BuildPackVersionCommand(KitLogger log) {
    super(log);
  }

  @Override
  protected String[] getArgs() {
    return new String[] { getApplicableBinary(PACK_CLI_ARTIFACT_PREFIX), "--version" };
  }

  @Override
  protected void processLine(String line) {
    version = line;
  }
}
