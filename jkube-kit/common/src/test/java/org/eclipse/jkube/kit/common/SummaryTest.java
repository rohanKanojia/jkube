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
package org.eclipse.jkube.kit.common;

import org.eclipse.jkube.kit.common.summary.Summary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class SummaryTest {
  private KitLogger logger;
//
//  @BeforeEach
//  public void setUp() {
//    logger = spy(new KitLogger.SilentLogger());
//    Summary.clear();
//  }
//
//  @Test
//  void getInstance_whenCalled_shouldAlwaysInitializeObject() {
//    // Given + When
//    Summary summary = Summary.getInstance();
//
//    // Then
//    assertThat(summary).isNotNull();
//  }
//
//  @Test
//  void printSummary_whenInvoked_shouldPrintSummary() {
//    // Given
//    Summary summary = Summary.getInstance();
//    initializeSummary(summary);
//
//    // When
//    summary.printSummary(logger, true);
//
//    // Then
//    verifySummaryPrintedOnce();
//  }
//
//  @Test
//  void printSummary_whenInvokedMultileTimes_shouldPrintSummaryOnlyOnce() {
//    // Given
//    Summary summary = Summary.getInstance();
//    initializeSummary(summary);
//
//    // When
//    summary.printSummary(logger, true);
//    summary.printSummary(logger, true);
//    summary.printSummary(logger, true);
//    summary.printSummary(logger, true);
//
//    // Then
//    verifySummaryPrintedOnce();
//  }
//
//  @Test
//  void printSummary_whenFailure_shouldPrintFailureAndCause() {
//    // Given
//    Summary summary = Summary.getInstance();
//    summary.setSuccessful(false);
//    summary.setFailureCause("failure in pulling image");
//
//    // When
//    summary.printSummary(logger, true);
//
//    // Then
//    verify(logger).info("[[C]] FAILURE [[C]]");
//    verify(logger).info("[[C]] Failure cause : failure in pulling image [[C]]");
//  }
//
//  @Test
//  void printSummary_whenSummaryEnabledFalse_shouldNotPrintAnything() {
//    // Given
//    Summary summary = Summary.getInstance();
//
//    // When
//    summary.printSummary(logger, false);
//
//    // Then
//    verify(logger, times(0)).info(anyString());
//  }
//
//  private void initializeSummary(Summary summary) {
//    summary.setBuildStrategy("Docker");
//    summary.setPackagedFileLocation("/tmp/foo.jar");
//    summary.setGeneratorName("java-exec");
//    summary.setDockerFileUsed(null);
//    summary.setTargetImageName("quay.io/example/test:latest");
//    summary.setPushRegistry("quay.io");
//    summary.setPushRegistryUser("example");
//    summary.setPushedImage("quay.io/example/test:latest");
//    summary.addGeneratedResourceFile("/tmp/target/classes/META-INF/jkube/kubernetes/test-deployment.yml");
//    summary.addGeneratedResourceFile("/tmp/target/classes/META-INF/jkube/kubernetes/test-service.yml");
//    summary.setAggregateResourceFile("/tmp/target/classes/META-INF/jkube/kubernetes.yml");
//    summary.addCreatedKubernetesResource("apps/v1 Deployment test-ns/test");
//    summary.addCreatedKubernetesResource("v1 Service test-ns/test");
//    summary.addUpdatedKubernetesResource("v1 Service test-ns/test");
//    summary.setHelmChartName("test");
//    summary.setHelmChartCompressedLocation("/tmp/target/test.tar.gz");
//    summary.setHelmChartLocation("/tmp/target/jkube/helm/test/kubernetes");
//    summary.setHelmRepository("localhost:8001/api/charts");
//    summary.addDeletedKubernetesResource("apps/v1 Deployment test-ns/test");
//    summary.addCreatedKubernetesResource("v1 Service test-ns/test");
//    summary.setTargetClusterUrl("https://192.168.39.75:8443/");
//    summary.setSuccessful(true);
//  }
//
//  private void verifySummaryPrintedOnce() {
//    verifySummaryBannerPrinted();
//    verifyBuildAndPushSummaryPrinted();
//    verifyResourceSummaryPrinted();
//    verifyHelmSummaryPrinted();
//    verifyApplyUndeploySummaryPrinted();
//    verify(logger).info("[[C]] SUCCESS [[C]]");
//  }
//
//  private void verifySummaryBannerPrinted() {
//    verify(logger, times(3)).info("[[C]] ------------------------------- [[C]]");
//    verify(logger).info("[[C]]       SUMMARY [[C]]");
//    verify(logger).info("[[C]]  __ / / //_/ / / / _ )/ __/ [[C]]");
//    verify(logger).info("[[C]] / // / ,< / /_/ / _  / _/   [[C]]");
//    verify(logger).info("[[C]] \\___/_/|_|\\____/____/___/  \n [[C]]");
//  }
//
//  private void verifyBuildAndPushSummaryPrinted() {
//    verify(logger).info("[[C]] Build Strategy : Docker [[C]]");
//    verify(logger).info("[[C]] Packaged File Location : /tmp/foo.jar [[C]]");
//    verify(logger).info("[[C]] Generator Name : java-exec [[C]]");
//    verify(logger).info("[[C]] Target Image Name : quay.io/example/test:latest [[C]]");
//    verify(logger).info("[[C]] Pushed to Image Registry: quay.io [[C]]");
//    verify(logger).info("[[C]] Username : example [[C]]");
//    verify(logger).info("[[C]] Pushed Image URL : quay.io/example/test:latest [[C]]");
//  }
//
//  private void verifyResourceSummaryPrinted() {
//    verify(logger).info("[[C]] Generated Resource Files:\n [[C]]");
//    verify(logger).info("[[C]] Individual :\n [[C]]");
//    verify(logger).info("[[C]]  - /tmp/target/classes/META-INF/jkube/kubernetes/test-deployment.yml [[C]]");
//    verify(logger).info("[[C]]  - /tmp/target/classes/META-INF/jkube/kubernetes/test-service.yml [[C]]");
//    verify(logger).info("[[C]] Aggregate : /tmp/target/classes/META-INF/jkube/kubernetes.yml [[C]]");
//  }
//
//  private void verifyHelmSummaryPrinted() {
//    verify(logger).info("[[C]] Helm Chart Name : test [[C]]");
//    verify(logger).info("[[C]] Helm Chart (Compressed) : /tmp/target/test.tar.gz [[C]]");
//    verify(logger).info("[[C]] Helm Chart Directory : /tmp/target/jkube/helm/test/kubernetes [[C]]");
//    verify(logger).info("[[C]] Helm Repository : localhost:8001/api/charts [[C]]");
//  }
//
//  private void verifyApplyUndeploySummaryPrinted() {
//    verify(logger).info("[[C]] Cluster URL : https://192.168.39.75:8443/ [[C]]");
//    verify(logger).info("[[C]] Created Resources: \n [[C]]");
//    verify(logger).info("[[C]] Deleted Resources: \n [[C]]");
//    verify(logger).info("[[C]] Updated Resources: \n [[C]]");
//    verify(logger, times(2)).info("[[C]]  - apps/v1 Deployment test-ns/test [[C]]");
//    verify(logger, times(3)).info("[[C]]  - v1 Service test-ns/test [[C]]");
//  }
}
