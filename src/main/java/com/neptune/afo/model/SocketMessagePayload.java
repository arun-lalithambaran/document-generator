package com.neptune.afo.model;

import com.fasterxml.jackson.databind.jsonschema.JsonSerializableSchema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SocketMessagePayload {
  private String from;
  private List<String> recipients;
  private String event;
  private Object message;
}
