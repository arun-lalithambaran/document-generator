package com.neptune.afo.specifications;

import com.neptune.afo.entity.Story;
import com.neptune.afo.entity.User;
import com.neptune.afo.model.StoryFilterParams;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class StorySpecification {
  public static Specification<Story> getStories(StoryFilterParams filter) {
    Specification<Story> specification = Specification.where(getByUser(filter.getUser()));
    specification = specification.and(getByArchived(filter.getShowArchived()));
    if (StringUtils.hasLength(filter.getSearch()))
      specification = specification.and(getByTitle(filter.getSearch()));
    return specification;
  }

  public static Specification<Story> getByUser(User user) {
    return ((root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("createdBy"), user));
  }

  public static Specification<Story> getByArchived(Boolean archived) {
    return ((root, query, criteriaBuilder) ->
        criteriaBuilder.equal(root.get("archived"), archived));
  }

  public static Specification<Story> getByTitle(String searchText) {
    return ((root, query, criteriaBuilder) ->
        criteriaBuilder.like(root.get("title"), "%" + searchText + "%"));
  }
}
