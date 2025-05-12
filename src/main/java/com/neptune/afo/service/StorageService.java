package com.neptune.afo.service;

import com.google.cloud.storage.*;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class StorageService {

  private final String bucketName = "afo-neptune-storage";
  private final String baseFolder = "neptune";

  @Autowired Storage storage;

  //    public List<Blob> listFiles() {
  //        Page<Blob> list = storage.list(bucketName);
  //        return List.of(list.getValues());
  //    }
  public String uploadFile(String fileName, byte[] fileBytes) throws IOException {
    String filePath = String.format("%s/%s", baseFolder, fileName);
    BlobId blobId = BlobId.of(bucketName, filePath);
    BlobInfo blobInfo = BlobInfo.newBuilder(blobId).build();
    storage.create(blobInfo, fileBytes);
    return filePath;
  }

  public byte[] downloadFile(String fileName) throws IOException {
    BlobId blobId = BlobId.of(bucketName, fileName);
    Blob blob = storage.get(blobId);
    return blob.getContent();
  }
}
