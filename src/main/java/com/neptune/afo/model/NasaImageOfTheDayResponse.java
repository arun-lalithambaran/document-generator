package com.neptune.afo.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDate;

@Data
public class NasaImageOfTheDayResponse {
    private String copyright;
    private LocalDate date;
    private String explanation;
    @JsonProperty("hdurl")
    private String hdUrl;
    private String mediaType;
    private String serviceVersion;
    private String title;
    private String url;
}
