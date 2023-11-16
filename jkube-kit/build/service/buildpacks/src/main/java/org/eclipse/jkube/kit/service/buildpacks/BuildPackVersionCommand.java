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
import org.apache.commons.lang3.StringUtils;
import org.eclipse.jkube.kit.common.ExternalCommand;
import org.eclipse.jkube.kit.common.KitLogger;

import java.util.ArrayList;
import java.util.List;

import static org.eclipse.jkube.kit.common.util.GitHubCliDownloaderUtil.getCLIDownloadPlatformApplicableBinary;

@Getter
public class BuildPackVersionCommand extends ExternalCommand {
  private String version;
  private final String packCliPath;
  private static final String PACK_CLI_ARTIFACT_PREFIX = "pack";

  protected BuildPackVersionCommand(KitLogger log, String packCliPath) {
    super(log);
    this.packCliPath = packCliPath;
  }

  @Override
  protected String[] getArgs() {
    List<String> args = new ArrayList<>();
    if (StringUtils.isNotBlank(packCliPath)) {
      args.add(packCliPath);
    } else {
      args.add(getCLIDownloadPlatformApplicableBinary(PACK_CLI_ARTIFACT_PREFIX));
    }
    args.add("--version");
    return args.toArray(new String[0]);
  }

  @Override
  protected void processLine(String line) {
    version = line;
  }
}
