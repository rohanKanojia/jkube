package org.eclipse.jkube.kit.common.summary;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
public class KubernetesResourceSummary {
  private String resourceName;
  private String group;
  private String version;
  private String kind;
  private String namespace;

  public KubernetesResourceSummary(String resourceName, String group, String version, String kind, String namespace) {
    this.resourceName = resourceName;
    this.group = group;
    this.version = version;
    this.kind = kind;
    this.namespace = namespace;
  }
}
