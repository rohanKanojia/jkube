/**
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
package org.eclipse.jkube.gradle.plugin.tests;

import net.minidev.json.parser.ParseException;
import org.eclipse.jkube.kit.common.ResourceVerify;
import org.gradle.testkit.runner.BuildResult;
import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

public class DebugModeIT {
  @Rule
  public final ITGradleRunner gradleRunner = new ITGradleRunner();

  @Test
  public void k8sResource_whenRun_generatesK8sManifests() throws IOException, ParseException {
    // When
    final BuildResult result = gradleRunner.withITProject("debug-mode").withArguments("k8sResource").build();
    // Then
    ResourceVerify.verifyResourceDescriptors(gradleRunner.resolveDefaultKubernetesResourceFile(),
        gradleRunner.resolveFile("expected", "kubernetes.yml"));
    assertThat(result).extracting(BuildResult::getOutput).asString()
        .contains("Using resource templates from")
        .contains("Adding a default Deployment")
        .contains("Adding revision history limit to 2")
        .contains("Enabling debug on")
        .contains("validating")
        .contains("SUMMARY")
        .contains("Generated Resource Files:")
        .contains("Individual :")
        .contains("build/classes/java/main/META-INF/jkube/kubernetes/debug-mode-deployment.yml")
        .contains("Aggregate : ")
        .contains("build/classes/java/main/META-INF/jkube/kubernetes.yml")
        .contains("SUCCESS");
  }

  @Test
  public void ocResource_whenRun_generatesK8sManifests() throws IOException, ParseException {
    // When
    final BuildResult result = gradleRunner.withITProject("debug-mode").withArguments("ocResource").build();
    // Then
    ResourceVerify.verifyResourceDescriptors(gradleRunner.resolveDefaultOpenShiftResourceFile(),
        gradleRunner.resolveFile("expected", "openshift.yml"));
    assertThat(result).extracting(BuildResult::getOutput).asString()
        .contains("Using resource templates from")
        .contains("Adding a default Deployment")
        .contains("Adding revision history limit to 2")
        .contains("Enabling debug on")
        .contains("validating")
        .contains("SUMMARY")
        .contains("Generated Resource Files:")
        .contains("Individual :")
        .contains("build/classes/java/main/META-INF/jkube/openshift/debug-mode-deploymentconfig.yml")
        .contains("Aggregate : ")
        .contains("build/classes/java/main/META-INF/jkube/openshift.yml")
        .contains("SUCCESS");
  }
}
