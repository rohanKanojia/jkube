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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class SHAUtil {
  private static final int BUFFER_SIZE = 8192;
  private static final String SHA_256 = "SHA-256";
  private SHAUtil() { }

  public static String generateSHA256(File file) throws IOException {
    return generateSHA(file, SHA_256);
  }

  public static String generateSHA256(String input) {
    MessageDigest digest = createNewMessageDigest(SHA_256);
    return bytesToHex(digest.digest(input.getBytes(StandardCharsets.UTF_8)));
  }

  public static String generateSHA(File file, String algorithm) throws IOException {
    try (InputStream input = Files.newInputStream(file.toPath())) {
      MessageDigest shaDigest = createNewMessageDigest(algorithm);
      byte[] buffer = new byte[BUFFER_SIZE];
      int len = input.read(buffer);

      while (len != -1) {
        shaDigest.update(buffer, 0, len);
        len = input.read(buffer);
      }

      return bytesToHex(shaDigest.digest());
    }
  }

  static MessageDigest createNewMessageDigest(String algorithm) {
    try {
      return MessageDigest.getInstance(algorithm);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalArgumentException("Error while generating SHA, no such algorithm : " + algorithm);
    }
  }

  private static String bytesToHex(byte[] hash) {
    StringBuilder hexString = new StringBuilder(2 * hash.length);
    for (byte b : hash) {
      String hex = Integer.toHexString(0xff & b);
      if (hex.length() == 1) {
        hexString.append('0');
      }
      hexString.append(hex);
    }
    return hexString.toString();
  }
}
