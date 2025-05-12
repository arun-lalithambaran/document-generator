package com.neptune.afo.controller;

import com.neptune.afo.constant.AppConstant;
import com.neptune.afo.exception.NotFoundException;
import com.neptune.afo.model.CommonResponse;
import com.neptune.afo.service.FileManagerService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@SecurityRequirement(name = AppConstant.AUTHORIZATION)
@RestController
@RequestMapping("api/fileManager")
public class FileManagerController {

    @Autowired private FileManagerService fileManagerService;

    @PostMapping(value = "upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CommonResponse<String> uploadFile(@RequestParam("file") MultipartFile multipartFile) {

        if(multipartFile.isEmpty()) throw new NotFoundException("File not found");
        fileManagerService.uploadFile(multipartFile);
        return CommonResponse.create("File uploaded successfully");
    }
}
