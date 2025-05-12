package com.neptune.afo.model;

import lombok.*;

@AllArgsConstructor
@Data
@Builder
public class ErrorResponse {
  private String message;
  private int status;
}
