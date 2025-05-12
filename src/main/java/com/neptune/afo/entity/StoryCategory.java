package com.neptune.afo.entity;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "storyCategoryMaster")
@Getter
@Setter
public class StoryCategory {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Integer categoryId;

  private String category;

  private String color;

  private Boolean status;
}
