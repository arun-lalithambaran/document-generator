package com.neptune.afo.converter;

import com.neptune.afo.entity.Story;
import com.neptune.afo.model.StoryDTO;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class StoryResponseConverter {
  public List<StoryDTO.StoryResponse> convert(List<Story> stories) {
    return stories.stream().map(this::convertToMinimal).collect(Collectors.toList());
  }

  public StoryDTO.StoryResponse convertToMinimal(Story story) {
    StoryDTO.Category storyCategory = null;
    if (story.getCategory() != null)
      storyCategory =
          StoryDTO.Category.builder()
              .categoryId(story.getCategory().getCategoryId())
              .category(story.getCategory().getCategory())
              .color(story.getCategory().getColor())
              .build();
    return StoryDTO.StoryResponse.builder()
        .storyId(story.getId())
        .title(story.getTitle())
        .category(storyCategory)
        .archived(story.getArchived())
        .createdAt(story.getCreatedAt())
        .modifiedAt(story.getUpdatedAt())
        .build();
  }

  public StoryDTO.StoryResponse convertToDetailed(Story story) {

    StoryDTO.Category storyCategory = null;
    if (story.getCategory() != null)
      storyCategory =
          StoryDTO.Category.builder()
              .categoryId(story.getCategory().getCategoryId())
              .category(story.getCategory().getCategory())
              .color(story.getCategory().getColor())
              .build();
    return StoryDTO.StoryResponse.builder()
        .storyId(story.getId())
        .title(story.getTitle())
        .storyContent(story.getStoryContent())
        .category(storyCategory)
        .archived(story.getArchived())
        .createdAt(story.getCreatedAt())
        .modifiedAt(story.getUpdatedAt())
        .createdBy(story.getCreatedBy().getId())
        .build();
  }
}
