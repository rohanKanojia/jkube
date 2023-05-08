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
package org.eclipse.jkube.kit.resource.helm;

import org.eclipse.jkube.kit.common.KitLogger;
import org.eclipse.jkube.kit.common.util.SHAUtil;
import org.eclipse.jkube.kit.resource.helm.oci.OCIRegistryClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedConstruction;

import java.io.File;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OCIUploaderTest {
  private OCIUploader ociUploader;
  private KitLogger logger;
  private HelmConfig helmConfig;
  private HelmRepository helmRepository;
  @TempDir
  private File tempDir;
  private File chartFile;
  private String chartTarballContentDigest;

  @BeforeEach
  void setUp() throws IOException {
    logger = spy(new KitLogger.SilentLogger());
    ociUploader = new OCIUploader(logger);
    helmRepository = HelmRepository.builder()
        .url("https://r.example.com/myuser")
        .username("myuser")
        .build();
    helmConfig = HelmConfig.builder()
        .chart("test-chart")
        .version("0.0.1")
        .stableRepository(helmRepository)
        .build();
    chartFile = new File(tempDir, "test-chart-0.0.1.tar.gz");
    assertThat(chartFile.createNewFile()).isTrue();
    chartTarballContentDigest = SHAUtil.generateSHA256(chartFile);
  }

  @Test
  void uploadSingle_whenChartBlobsAlreadyUploaded_thenLogPushSkip() throws BadUploadException, IOException {
    try (MockedConstruction<OCIRegistryClient> ociMockedConstruction = mockConstruction(OCIRegistryClient.class, (mock, ctx) -> {
      when(mock.getBaseUrl()).thenReturn("https://r.example.com");
      when(mock.isLayerUploadedAlready(anyString(), anyString())).thenReturn(true);
      when(mock.uploadOCIManifest(anyString(), anyString(), anyString(), anyString(), anyInt(), anyLong())).thenReturn("sha256:uploadmanifestdigest");
    })) {
      // When
      ociUploader.uploadSingle(chartFile, helmConfig, helmRepository);

      // Then
      assertThat(ociMockedConstruction.constructed()).hasSize(1);
      verify(logger).info("Skipping push, BLOB already exists on target registry: %s", chartTarballContentDigest);
      verify(logger).info("Pushed: %s/%s/%s:%s", "https://r.example.com", "myuser", "test-chart", "0.0.1");
      verify(logger).info("Digest: %s", "sha256:uploadmanifestdigest");
    }
  }

  @Test
  void uploadSingle_whenChartPushFailed_thenThrowException() {
    try (MockedConstruction<OCIRegistryClient> ignore = mockConstruction(OCIRegistryClient.class, (mock, ctx) -> {
      when(mock.getBaseUrl()).thenReturn("https://r.example.com");
      when(mock.isLayerUploadedAlready(anyString(), anyString())).thenReturn(false);
      when(mock.initiateUploadProcess(anyString())).thenReturn("https://r.example.com/v2/myuser/blobs/uploads/random-uuid?state=testing");
      when(mock.uploadBlob(anyString(), anyString(), anyLong(), anyString(), any()))
          .thenThrow(new BadUploadException("invalid upload data"));
    })) {
      // When
      assertThatExceptionOfType(BadUploadException.class)
          .isThrownBy(() -> ociUploader.uploadSingle(chartFile, helmConfig, helmRepository))
          .withMessage("invalid upload data");
    }
  }

  @Test
  void uploadSingle_whenChartSuccessfullyPushedToRegistry_thenLogDockerContentManifest() throws BadUploadException, IOException {
    try (MockedConstruction<OCIRegistryClient> ociMockedConstruction = mockConstruction(OCIRegistryClient.class, (mock, ctx) -> {
      when(mock.getBaseUrl()).thenReturn("https://r.example.com");
      when(mock.isLayerUploadedAlready(anyString(), anyString())).thenReturn(false);
      when(mock.initiateUploadProcess(anyString())).thenReturn("https://r.example.com/v2/myuser/blobs/uploads/random-uuid?state=testing");
      when(mock.uploadBlob(anyString(), anyString(), anyLong(), isNull(), any())).thenReturn("sha256:charttarballdigest");
      when(mock.uploadBlob(anyString(), anyString(), anyLong(), anyString(), isNull())).thenReturn("sha256:chartconfigdigest");
      when(mock.uploadOCIManifest(anyString(), anyString(), anyString(), anyString(), anyInt(), anyLong())).thenReturn("sha256:uploadmanifestdigest");
    })) {
      // When
      ociUploader.uploadSingle(chartFile, helmConfig, helmRepository);

      // Then
      assertThat(ociMockedConstruction.constructed()).hasSize(1);
      verify(logger, times(1)).info("Pushed: %s/%s/%s:%s", "https://r.example.com", "myuser", "test-chart", "0.0.1");
      verify(logger).info("Digest: %s", "sha256:uploadmanifestdigest");
    }
  }
}
