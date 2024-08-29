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
package org.eclipse.jkube.kit.common.util;

import org.eclipse.jkube.kit.common.JavaProject;
import org.eclipse.jkube.kit.common.KitLogger;

import java.net.URL;
import java.util.Properties;

import static org.eclipse.jkube.kit.common.util.PropertiesUtil.fromApplicationConfigSource;
import static org.eclipse.jkube.kit.common.util.PropertiesUtil.mergeResourcePropertiesWithProjectProperties;
import static org.eclipse.jkube.kit.common.util.PropertiesUtil.readPropertiesFromResource;


public class ThorntailUtil {
    private static final String[] THORNTAIL_APP_CONFIG_FILES_LIST = new String[] {"project-defaults.yml"};
    private static final String THORNTAIL_HTTP_PORT_PROPERTY = "thorntail.http.port";

    private ThorntailUtil() {}

    public static Properties resolveThorntailAppConfigProperties(KitLogger log, JavaProject javaProject) {
        URL propertySource = fromApplicationConfigSource(javaProject, THORNTAIL_APP_CONFIG_FILES_LIST);
        if (propertySource != null) {
            log.info("Thorntail Application Config loaded from : %s", propertySource);
        }
        return mergeResourcePropertiesWithProjectProperties(readPropertiesFromResource(propertySource), javaProject);
    }

    public static String resolveThorntailWebPortFromThorntailConfig(Properties thorntailApplicationConfig) {
        thorntailApplicationConfig.putAll(System.getProperties());
        if (thorntailApplicationConfig.containsKey(THORNTAIL_HTTP_PORT_PROPERTY)) {
            return (String) thorntailApplicationConfig.get(THORNTAIL_HTTP_PORT_PROPERTY);
        }
        return null;
    }
}
