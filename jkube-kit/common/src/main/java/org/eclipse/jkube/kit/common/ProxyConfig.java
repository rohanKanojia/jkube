package org.eclipse.jkube.kit.common;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@EqualsAndHashCode
public class ProxyConfig {
  private String id;
  private boolean active;
  private String protocol;
  private String host;
  private int port;
  private String username;
  private String password;
  private String nonProxyHosts;
}
