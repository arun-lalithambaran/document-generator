package com.neptune.afo.model;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.Getter;

@Data
@Getter
public class FileDownloadRequest {
  @NotEmpty private String url;
  @NotEmpty private String fileName;
  @NotNull private Boolean secureConnection;
}
