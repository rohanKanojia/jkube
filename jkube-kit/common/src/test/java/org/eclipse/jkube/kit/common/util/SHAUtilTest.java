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
package org.eclipse.jkube.kit.common.util;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class SHAUtilTest {
  @Test
  void generateSHA256_whenFileProvided_shouldGenerateDigest() throws IOException {
    // Given
    File file = new File(Objects.requireNonNull(getClass().getResource("/crontab-cr.yml")).getFile());

    // When
    String result = SHAUtil.generateSHA256(file);

    // Then
    assertThat(result).isEqualTo("da723cee661dd0368f1b50274720e189372c336c2f78b9848826b7ff4f303b61");
  }

  @Test
  void generateSHA256_whenStringProvided_shouldGenerateDigest() {
    // Given + When
    String result = SHAUtil.generateSHA256("somerandomstring");

    // Then
    assertThat(result).isEqualTo("39b5931a5dd8980254782830a1134644035573a1d75a50527e233f45f6afc5ce");
  }

  @Test
  void createNewMessageDigest_whenValidAlgorithmProvided_thenCreateMessageDigestObj() {
    // Given
    String algorithm = "SHA-256";
    // When
    MessageDigest digest = SHAUtil.createNewMessageDigest(algorithm);
    // Then
    assertThat(digest.getAlgorithm()).isEqualTo(algorithm);
  }

  @Test
  void createNewMessageDigest_whenInvalidAlgorithmProvided_thenThrowException() {
    // Given
    String algorithm = "invalid";
    // When + Then
    assertThatIllegalArgumentException()
        .isThrownBy(() -> SHAUtil.createNewMessageDigest(algorithm))
        .withMessage("Error while generating SHA, no such algorithm : invalid");
  }
}
