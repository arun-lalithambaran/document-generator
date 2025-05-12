package com.neptune.afo.entity;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "story")
public class Story {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "storyId", nullable = false)
  private Long id;

  @Column(name = "title", nullable = false)
  private String title;

  @Lob
  @Column(name = "storyContent")
  private String storyContent;

  @ManyToOne
  @JoinColumn(name = "categoryId")
  private StoryCategory category;

  private Boolean archived;

  private Boolean featured;

  @Column(name = "createdAt", nullable = false)
  private Instant createdAt;

  @Column(name = "updatedAt", nullable = false)
  private Instant updatedAt;

  @ManyToOne
  @JoinColumn(name = "createdBy", nullable = false)
  private User createdBy;
}
