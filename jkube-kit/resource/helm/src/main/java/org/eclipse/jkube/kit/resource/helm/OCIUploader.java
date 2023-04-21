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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.jkube.kit.common.KitLogger;
import org.eclipse.jkube.kit.common.util.SHAUtil;
import org.eclipse.jkube.kit.resource.helm.oci.OCIRegistryClient;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;

public class OCIUploader {
  private static final ObjectMapper objectMapper = new ObjectMapper();
  private final KitLogger logger;

  public OCIUploader(KitLogger logger) {
    this.logger = logger;
  }

  protected void uploadSingle(File file, HelmConfig helmConfig, HelmRepository repository)
      throws IOException, BadUploadException {
    Chart chartConfig = createChartFromHelmConfig(helmConfig);
    OCIRegistryClient oci = new OCIRegistryClient(logger, repository);

    oci.verifyIfAuthorizedToPushChart(chartConfig.getName(), file);
    uploadChartToOCIRegistry(oci, chartConfig, repository, file);
  }

  private void uploadChartToOCIRegistry(OCIRegistryClient oci, Chart chartConfig, HelmRepository repository, File file) throws IOException, BadUploadException {
    String chartTarballBlobDigest = SHAUtil.generateSHA256(file);
    String chartConfigBlobDigest = SHAUtil.generateSHA256(objectMapper.writeValueAsString(chartConfig));
    String chartConfigContentPayload = objectMapper.writeValueAsString(chartConfig);
    int chartConfigPayloadSize = chartConfigContentPayload.getBytes(Charset.defaultCharset()).length;

    String chartTarballDockerContentDigest = uploadBlobIfNotExist(oci, chartConfig.getName(), chartTarballBlobDigest, file, null);
    String chartConfigDockerContentDigest = uploadBlobIfNotExist(oci, chartConfig.getName(), chartConfigBlobDigest, null, chartConfigContentPayload);

    String manifestDockerContentDigest = oci.uploadOCIManifest(chartConfig.getName(), chartConfig.getVersion(), chartConfigDockerContentDigest, chartTarballDockerContentDigest, chartConfigPayloadSize, file.length());
    logger.info("Pushed: %s/%s/%s:%s", oci.getBaseUrl(), repository.getUsername(), chartConfig.getName(), chartConfig.getVersion());
    logger.info("Digest: %s", manifestDockerContentDigest);
  }

  private String uploadBlobIfNotExist(OCIRegistryClient oci, String chartName, String blob, File blobContentFile, String blobContentStr) throws IOException, BadUploadException {
    boolean alreadyUploaded = oci.isLayerUploadedAlready(chartName, blob);
    if (alreadyUploaded) {
      logger.info("Skipping push, BLOB already exists on target registry: %s", blob);
      return String.format("sha256:%s", blob);
    } else {
      return uploadBlob(oci, chartName, blob, blobContentFile, blobContentStr);
    }
  }

  private String uploadBlob(OCIRegistryClient oci, String chartName, String blob, File blobContentFile, String blobContentStr) throws IOException, BadUploadException {
    String uploadUrl = oci.initiateUploadProcess(chartName);
    return oci.uploadBlob(uploadUrl, blob, blobContentFile, blobContentStr);
  }

  private Chart createChartFromHelmConfig(HelmConfig helmConfig) {
    return Chart.builder()
        .apiVersion("v1")
        .name(helmConfig.getChart())
        .home(helmConfig.getHome())
        .sources(helmConfig.getSources())
        .version(helmConfig.getVersion())
        .description(helmConfig.getDescription())
        .maintainers(helmConfig.getMaintainers())
        .build();
  }
}
