package org.eclipse.jkube.kit.common;

import java.util.List;

public class ProxyUtil {
  public static ProxyConfig getApplicableProxy(String protocol, List<ProxyConfig> proxyConfigFromSettings) {
    ProxyConfig propertiesConfiguredProxy = getProxyConfigFromSystemProperties(protocol);
    if (propertiesConfiguredProxy != null) {
      return propertiesConfiguredProxy;
    }
    return getProxyConfigFromSettings(protocol, proxyConfigFromSettings);
  }

  private static ProxyConfig getProxyConfigFromSettings(String protocol, List<ProxyConfig> proxyConfigFromSettings) {
    for (ProxyConfig proxyConfig : proxyConfigFromSettings) {
      if (proxyConfig.getProtocol().equalsIgnoreCase(protocol)) {
        return proxyConfig;
      }
    }
    return null;
  }

  private static ProxyConfig getProxyConfigFromSystemProperties(String protocol) {
    if (System.getProperty(protocol + ".proxyUser") != null && System.getProperty(protocol + ".proxyPassword") != null) {
      ProxyConfig.ProxyConfigBuilder proxyConfigBuilder = ProxyConfig.builder();
      if (System.getProperty(protocol + ".proxyUser") != null) {
        proxyConfigBuilder.username(System.getProperty(protocol + ".proxyUser"));
      }
      if (System.getProperty(protocol + ".proxyPassword") != null) {
        proxyConfigBuilder.password(System.getProperty(protocol + ".proxyPassword"));
      }
      if (System.getProperty(protocol + ".proxyHost") != null) {
        proxyConfigBuilder.host(System.getProperty(protocol + ".proxyHost"));
      }
      if (System.getProperty(protocol + ".proxyPort") != null) {
        proxyConfigBuilder.port(Integer.parseInt(System.getProperty(protocol + ".proxyPort")));
      }
      proxyConfigBuilder.protocol(protocol);
      return proxyConfigBuilder.build();
    }
    return null;
  }
}
