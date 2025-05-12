package com.neptune.afo.model;

import com.neptune.afo.entity.User;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class StoryFilterParams {
  Integer page;
  Integer pageSize;
  String search;
  Boolean showArchived;
  User user;
}
