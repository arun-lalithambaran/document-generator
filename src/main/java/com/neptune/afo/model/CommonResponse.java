package com.neptune.afo.model;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CommonResponse<T> {
  private String message;
  private T data;

  public static <T> CommonResponse<T> create(T t, String message) {
    return new CommonResponse<>(message, t);
  }

  public static <T> CommonResponse<T> create(String message) {
    return new CommonResponse<>(message, null);
  }

  public static <T> CommonResponse<T> create(T t) {
    return new CommonResponse<>(null, t);
  }
}
