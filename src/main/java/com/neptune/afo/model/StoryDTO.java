package com.neptune.afo.model;

import java.time.Instant;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class StoryDTO {

  @Data
  @Builder
  public static class StoryRequest {
    private String title;
    private String storyContent;
    private Boolean archived;
    private Integer category;
  }

  @Data
  @Builder
  public static class StoryResponse {
    private Long storyId;
    private String title;
    private String storyContent;
    private Boolean archived;
    private Category category;
    private Instant createdAt;
    private Long createdBy;
    private Instant modifiedAt;
  }

  @Data
  @Builder
  public static class Category {
    private Integer categoryId;
    private String category;
    private String color;
  }
}
