package com.neptune.afo.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class OnlineUser {
  private String name;
  private String email;
  private String sessionId;
}
