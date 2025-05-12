package com.neptune.afo.controller;

import com.neptune.afo.constant.AppConstant;
import com.neptune.afo.model.CommonResponse;
import com.neptune.afo.model.StoryDTO;
import com.neptune.afo.model.StoryFilterParams;
import com.neptune.afo.service.StoryService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

@SecurityRequirement(name = AppConstant.AUTHORIZATION)
@RestController
@RequestMapping("api/story")
public class StoryController {

  @Autowired StoryService storyService;

  @PostMapping
  public CommonResponse<StoryDTO.StoryResponse> createStory(
      @RequestBody StoryDTO.StoryRequest storyRequest) {
    StoryDTO.StoryResponse story = storyService.createStory(storyRequest);
    return CommonResponse.create(story, "Story saved successfully");
  }

  @PutMapping("{storyId}")
  public CommonResponse<StoryDTO.StoryResponse> updateStory(
      @PathVariable Long storyId, @RequestBody StoryDTO.StoryRequest storyRequest) {
    StoryDTO.StoryResponse story = storyService.update(storyId, storyRequest);
    return CommonResponse.create(story, "Story saved successfully");
  }

  @GetMapping("list")
  public CommonResponse<Page<StoryDTO.StoryResponse>> getAllStories(
      @RequestParam(defaultValue = "0") Integer page,
      @RequestParam(defaultValue = "100") Integer pageSize,
      @RequestParam(required = false) String search,
      @RequestParam(defaultValue = "false") Boolean showArchived) {
    return CommonResponse.create(
        storyService.getAllStories(
            StoryFilterParams.builder()
                .page(page)
                .pageSize(pageSize)
                .search(search)
                .showArchived(showArchived)
                .build()));
  }

  @GetMapping
  public CommonResponse<StoryDTO.StoryResponse> getStory(@RequestParam Long storyId) {
    return CommonResponse.create(storyService.getStory(storyId));
  }

  @GetMapping("/featured")
  public CommonResponse<StoryDTO.StoryResponse> getFeaturedStory() {
    return CommonResponse.create(storyService.getFeaturedStory());
  }

  @PutMapping("/featured/{storyId}")
  public CommonResponse<String> setFeaturedStory(@PathVariable Long storyId) {
    storyService.setFeaturedStory(storyId);
    return CommonResponse.create("Featured story updated");
  }

  @DeleteMapping("{storyId}/delete")
  public CommonResponse<String> deleteStory(@PathVariable Long storyId) {
    storyService.deleteStory(storyId);
    return CommonResponse.create("Story deleted successfully");
  }
}
