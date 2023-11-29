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

import org.eclipse.jkube.kit.common.ExternalCommand;
import org.eclipse.jkube.kit.common.KitLogger;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class BuildPackCommand extends ExternalCommand {
  private final String packCliPath;
  private final List<String> commandLineArgs;
  private final Consumer<String> commandOutputConsumer;

  public BuildPackCommand(KitLogger log, String packCliPath, List<String> cmdArgs, Consumer<String> outputConsumer) {
    super(log);
    this.packCliPath = packCliPath;
    this.commandLineArgs = cmdArgs;
    this.commandOutputConsumer = outputConsumer;
  }

  @Override
  public String[] getArgs() {
    List<String> args = new ArrayList<>();
    args.add(packCliPath);
    args.addAll(commandLineArgs);
    return args.toArray(new String[0]);
  }

  @Override
  public void processLine(String line) {
    commandOutputConsumer.accept(line);
  }

  public int getExitCode() {
    return getStatusCode();
  }
}