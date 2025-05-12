package com.neptune.afo.controller;

import com.neptune.afo.constant.AppConstant;
import com.neptune.afo.entity.DashboardLink;
import com.neptune.afo.model.CommonResponse;
import com.neptune.afo.model.DashboardLinkResponse;
import com.neptune.afo.model.FileDownloadRequest;
import com.neptune.afo.model.NasaImageOfTheDayResponse;
import com.neptune.afo.repository.DashboardLinkRepository;
import com.neptune.afo.service.PublicResourceService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@SecurityRequirement(name = AppConstant.AUTHORIZATION)
@RestController
@RequestMapping("api/public")
public class PublicResourceController {

  @Autowired private PublicResourceService publicResourceService;
  @Autowired private DashboardLinkRepository dashboardLinkRepository;

  @GetMapping("image-of-the-day")
  public CommonResponse<NasaImageOfTheDayResponse> getImageOfTheDay() {
    return CommonResponse.create(publicResourceService.getImageOfTheDay());
  }

  @PostMapping("download-file")
  public CommonResponse<String> saveUrl(@RequestBody FileDownloadRequest payload) {
    return CommonResponse.create(publicResourceService.downloadFileFromUrl(payload));
    //    byte[] bytes = publicResourceService.downloadFileFromUrl(payload);
    //    return ResponseEntity.ok()
    //        .contentType(MediaType.APPLICATION_OCTET_STREAM)
    //        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" +
    // payload.getFileName())
    //        .body(new ByteArrayResource(bytes));
  }

  @GetMapping("dashboardLinks")
  public CommonResponse<List<DashboardLinkResponse>> getDashboardLinks() {
    List<DashboardLink> dashboardLinks = dashboardLinkRepository.findByStatusTrueOrderBySortOrder();
    List<DashboardLinkResponse> dashboardLinkResponseList =
        dashboardLinks.stream()
            .map(
                dashboardLink ->
                    DashboardLinkResponse.builder()
                        .title(dashboardLink.getTitle())
                        .url(dashboardLink.getUrl())
                        .iconClass(dashboardLink.getIconClass())
                        .isAppRoute(dashboardLink.getIsAppRoute())
                        .build())
            .toList();
    return CommonResponse.create(dashboardLinkResponseList);
  }
}
