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
package org.eclipse.jkube.helidon;

import org.eclipse.jkube.kit.common.JavaProject;
import org.eclipse.jkube.kit.common.KitLogger;
import org.eclipse.jkube.kit.common.util.JKubeProjectUtil;

import java.net.URL;
import java.util.Properties;

import static org.eclipse.jkube.kit.common.util.PropertiesUtil.fromApplicationConfigSource;
import static org.eclipse.jkube.kit.common.util.PropertiesUtil.mergeResourcePropertiesWithProjectProperties;
import static org.eclipse.jkube.kit.common.util.PropertiesUtil.readPropertiesFromResource;

public class HelidonUtils {
  private static final String HELIDON_HTTP_PORT = "server.port";
  private static final String[] HELIDON_APP_CONFIG_FILES_LIST = new String[] {"META-INF/microprofile-config.properties", "application.yaml", "application.yml"};

  private HelidonUtils() { }

  public static boolean hasHelidonDependencies(JavaProject javaProject) {
    return JKubeProjectUtil.hasTransitiveDependency(javaProject, "io.helidon.webserver", "helidon-webserver");
  }

  public static boolean hasHelidonGraalNativeImageExtension(JavaProject javaProject) {
    return JKubeProjectUtil.hasDependency(javaProject, "io.helidon.integrations.graal", "helidon-graal-native-image-extension") ||
        JKubeProjectUtil.hasDependency(javaProject, "io.helidon.integrations.graal", "helidon-mp-graal-native-image-extension");
  }

  public static boolean hasHelidonHealthDependency(JavaProject javaProject) {
    return JKubeProjectUtil.hasTransitiveDependency(javaProject, "io.helidon.health", "helidon-health");
  }

  public static Properties resolveHelidonApplicationConfigProperties(KitLogger log, JavaProject javaProject) {
    URL propertySource = fromApplicationConfigSource(javaProject, HELIDON_APP_CONFIG_FILES_LIST);
    if (propertySource != null) {
      log.debug("Helidon Application Config loaded from : %s", propertySource);
    }
    return mergeResourcePropertiesWithProjectProperties(readPropertiesFromResource(propertySource), javaProject);
  }

  public static String extractPort(Properties properties, String defaultValue) {
    return properties.getProperty(HELIDON_HTTP_PORT, defaultValue);
  }
}
