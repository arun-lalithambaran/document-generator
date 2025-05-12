package com.neptune.afo.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@Slf4j
public class FileManagerService {
  public void uploadFile(MultipartFile multipartFile) {
    String fileName = multipartFile.getOriginalFilename();
    String destinationDir = System.getProperty("user.dir");
    log.info(String.format("File upload path : %s", destinationDir));
    Path destinationPath = Path.of(destinationDir);
    Path filePath = destinationPath.resolve(fileName);
    try {
      Files.copy(multipartFile.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
