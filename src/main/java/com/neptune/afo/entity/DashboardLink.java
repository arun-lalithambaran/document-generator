package com.neptune.afo.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Getter;

@Entity(name = "dashboardLinks")
@Getter
public class DashboardLink {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Integer id;

  private String title;
  private String url;
  private String iconClass;
  private Boolean isAppRoute;
  private Integer sortOrder;
  private Boolean status;
}
