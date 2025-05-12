package com.neptune.afo.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DashboardLinkResponse {
  private String title;
  private String url;
  private String iconClass;
  private Boolean isAppRoute;
  private Boolean isPrivate;
}
