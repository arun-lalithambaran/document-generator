package com.neptune.afo.service;

import com.neptune.afo.converter.StoryResponseConverter;
import com.neptune.afo.entity.Story;
import com.neptune.afo.entity.StoryCategory;
import com.neptune.afo.entity.User;
import com.neptune.afo.exception.NotFoundException;
import com.neptune.afo.model.StoryDTO;
import com.neptune.afo.model.StoryFilterParams;
import com.neptune.afo.repository.StoryCategoryRepository;
import com.neptune.afo.repository.StoryRepository;
import com.neptune.afo.specifications.StorySpecification;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
public class StoryService {

  @Autowired StoryRepository storyRepository;
  @Autowired StoryCategoryRepository storyCategoryRepository;
  @Autowired JwtService jwtService;
  @Autowired StoryResponseConverter storyConverter;

  public StoryDTO.StoryResponse createStory(StoryDTO.StoryRequest storyRequest) {
    User signedInUser = jwtService.getSignedInUserDetails();
    Story story = new Story();
    Optional<StoryCategory> category = storyCategoryRepository.findById(storyRequest.getCategory());
    category.ifPresent(story::setCategory);
    story.setTitle(storyRequest.getTitle());
    story.setArchived(storyRequest.getArchived());
    story.setStoryContent(storyRequest.getStoryContent());
    story.setCreatedAt(Instant.now());
    story.setUpdatedAt(Instant.now());
    story.setCreatedBy(signedInUser);
    return storyConverter.convertToDetailed(storyRepository.save(story));
  }

  public StoryDTO.StoryResponse update(Long storyId, StoryDTO.StoryRequest storyRequest) {
    Story story = getStoryEntity(storyId);
    story.setTitle(storyRequest.getTitle());
    Optional<StoryCategory> category = storyCategoryRepository.findById(storyRequest.getCategory());
    category.ifPresent(story::setCategory);
    story.setArchived(storyRequest.getArchived());
    story.setStoryContent(storyRequest.getStoryContent());
    story.setUpdatedAt(Instant.now());
    return storyConverter.convertToDetailed(storyRepository.save(story));
  }

  public Page<StoryDTO.StoryResponse> getAllStories(StoryFilterParams params) {
    User user = jwtService.getSignedInUserDetails();
    params.setUser(user);
    Sort sort = Sort.by(Sort.Direction.DESC, "updatedAt");
    PageRequest pageRequest = PageRequest.of(params.getPage(), params.getPageSize(), sort);
    Page<Story> stories =
        storyRepository.findAll(StorySpecification.getStories(params), pageRequest);
    return stories.map(st -> storyConverter.convertToMinimal(st));
  }

  public StoryDTO.StoryResponse getStory(Long storyId) {
    Story story = getStoryEntity(storyId);
    return storyConverter.convertToDetailed(story);
  }

  public StoryDTO.StoryResponse getFeaturedStory() {
    User user = jwtService.getSignedInUserDetails();
    Optional<Story> story =
        storyRepository.findByCreatedByAndFeaturedTrue(user).stream().findFirst();
    return story.map(value -> storyConverter.convertToDetailed(value)).orElse(null);
  }

  public void setFeaturedStory(Long storyId) {
    User user = jwtService.getSignedInUserDetails();
    List<Story> oldFeaturedStories = storyRepository.findByCreatedByAndFeaturedTrue(user);
    oldFeaturedStories.forEach(s -> s.setFeatured(Boolean.FALSE));
    storyRepository.saveAll(oldFeaturedStories);
    Story newFeaturedStory = getStoryEntity(storyId);
    newFeaturedStory.setFeatured(Boolean.TRUE);
    storyRepository.save(newFeaturedStory);
  }

  public void deleteStory(Long storyId) {
    Story story =
        storyRepository
            .findById(storyId)
            .orElseThrow(() -> new NotFoundException("Story not found"));
    storyRepository.delete(story);
  }

  private Story getStoryEntity(Long storyId) {
    return storyRepository
        .findById(storyId)
        .orElseThrow(() -> new NotFoundException("Story not found"));
  }
}
