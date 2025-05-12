package com.neptune.afo.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neptune.afo.entity.PublicResourceData;
import com.neptune.afo.exception.NotFoundException;
import com.neptune.afo.model.FileDownloadRequest;
import com.neptune.afo.model.NasaImageOfTheDayResponse;
import com.neptune.afo.repository.PublicResourceDataRepository;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Optional;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class PublicResourceService {

  @Value("${neptune.public.nasa.api_url}")
  private String nasaApiUrl;

  @Autowired ObjectMapper objectMapper;
  @Autowired PublicResourceDataRepository publicResourceDataRepository;
  @Autowired StorageService storageService;

  public NasaImageOfTheDayResponse getImageOfTheDay() {

    try {
      Optional<PublicResourceData> nasaImage =
          publicResourceDataRepository.findByTypeAndCreatedOn("NASA_IMAGE", new Date());
      if (nasaImage.isPresent()) {
        return objectMapper.readValue(
            nasaImage.get().getResponseData(), NasaImageOfTheDayResponse.class);
      } else {
        HttpRequest request =
            HttpRequest.newBuilder()
                .uri(URI.create(nasaApiUrl))
                .header("Accept", "*/*")
                .header("User-Agent", "AFO-NEPTUNE SERVER")
                .method("GET", HttpRequest.BodyPublishers.noBody())
                .build();
        HttpResponse<String> response =
            HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        PublicResourceData publicResourceData = new PublicResourceData();
        publicResourceData.setCreatedOn(Date.from(Instant.now()));
        publicResourceData.setType("NASA_IMAGE");
        publicResourceData.setResponseData(response.body());
        publicResourceDataRepository.save(publicResourceData);
        return objectMapper.readValue(response.body(), NasaImageOfTheDayResponse.class);
      }

    } catch (IOException e) {
      throw new NotFoundException("Something went wrong while fetching the resource");
    } catch (InterruptedException e) {
      throw new NotFoundException("Something went wrong while fetching the resource");
    }
  }

  public void saveUrl(String url) {
    PublicResourceData publicResourceData = new PublicResourceData();
    publicResourceData.setCreatedOn(Date.from(Instant.now()));
    publicResourceData.setType("URL");
    try {
      publicResourceData.setResponseData(
          objectMapper.writeValueAsString(
              new HashMap<>() {
                {
                  put("url", url);
                }
              }));
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
    publicResourceDataRepository.save(publicResourceData);
  }

  public String downloadFileFromUrl(FileDownloadRequest payload) {
    HttpRequest request =
        HttpRequest.newBuilder()
            .uri(URI.create(payload.getUrl()))
            .header("Accept", "*/*")
            .header("User-Agent", "AFO-NEPTUNE SERVER")
            .method("GET", HttpRequest.BodyPublishers.noBody())
            .build();
    String url;
    if (payload.getSecureConnection()) {
      url = downloadWithSecureConnection(request, payload);
    } else {
      url = downloadWithoutSecureConnection(request, payload);
    }
    saveUrl(url);
    return url;
  }

  private String downloadWithSecureConnection(HttpRequest request, FileDownloadRequest payload) {
    try {
      HttpResponse<InputStream> downloadResponse =
          HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofInputStream());
      byte[] fileBytes;
      try (InputStream responseBody = downloadResponse.body()) {
        fileBytes = responseBody.readAllBytes();
      }
      return storageService.uploadFile(payload.getFileName(), fileBytes);
    } catch (IOException | InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  private String downloadWithoutSecureConnection(HttpRequest request, FileDownloadRequest payload) {
    try {

      TrustManager[] trustAllCerts =
          new TrustManager[] {
            new X509TrustManager() {
              public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                return null;
              }

              public void checkClientTrusted(
                  java.security.cert.X509Certificate[] certs, String authType) {}

              public void checkServerTrusted(
                  java.security.cert.X509Certificate[] certs, String authType) {}
            }
          };

      // Install the all-trusting trust manager
      SSLContext sc = SSLContext.getInstance("SSL");
      sc.init(null, trustAllCerts, new java.security.SecureRandom());
      HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
      Path path = Path.of("/neptune-storage", payload.getFileName());
      HttpResponse<InputStream> downloadResponse =
          HttpClient.newBuilder()
              .sslContext(sc)
              .build()
              .send(request, HttpResponse.BodyHandlers.ofInputStream());
      byte[] fileBytes;
      try (InputStream responseBody = downloadResponse.body()) {
        fileBytes = responseBody.readAllBytes();
      }
      return storageService.uploadFile(payload.getFileName(), fileBytes);
    } catch (IOException
        | KeyManagementException
        | NoSuchAlgorithmException
        | InterruptedException e) {
      throw new RuntimeException(e);
    }
  }
}
