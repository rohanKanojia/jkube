package org.eclipse.jkube.kit.common.summary;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
public class ImageSummary {
  private String imageName;
  private String baseImageName;
  private String dockerfilePath;
  private String imageStreamUsed;
  private String imageSha;

  public ImageSummary(String imageName, String baseImageName, String dockerfilePath, String imageStreamUsed, String imageSha) {
    this.imageName = imageName;
    this.baseImageName = baseImageName;
    this.dockerfilePath = dockerfilePath;
    this.imageStreamUsed = imageStreamUsed;
    this.imageSha = imageSha;
  }
}
