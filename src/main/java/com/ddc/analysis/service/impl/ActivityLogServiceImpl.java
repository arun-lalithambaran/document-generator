package com.ddc.analysis.service.impl;

import com.ddc.analysis.entity.ActivityLog;
import com.ddc.analysis.entity.UserMaster;
import com.ddc.analysis.enums.EventType;
import com.ddc.analysis.repository.ActivityLogRepository;
import com.ddc.analysis.service.IActivityLogService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Date;
import java.util.stream.Collectors;

import static com.ddc.analysis.util.AppConstants.OUT_BOUND;
import static com.ddc.analysis.util.AppConstants.REST;

/** Created by sujith.g ON 08-12-2021 */
@Slf4j
@Service
public class ActivityLogServiceImpl implements IActivityLogService {

  @Autowired private ActivityLogRepository activityLogRepository;
  @Autowired private ObjectMapper objectMapper;

  @Override
  public ActivityLog saveActivityLog(ActivityLog activityLog) {
    return activityLogRepository.save(activityLog);
  }

  @Override
  public ActivityLog createActivityLog(
          StackTraceElement stackTraceElement, UserMaster user, String requestPayload, String endPoint) {
    ActivityLog activityLog = new ActivityLog();
    activityLog.setLogTime(new Date());
    activityLog.setClassName(stackTraceElement.getClassName());
    activityLog.setMethodName(stackTraceElement.getMethodName());
    activityLog.setUser(user);
    activityLog.setTypeOfTransaction(REST);
    activityLog.setTypeOfRequest(OUT_BOUND);
    activityLog.setRequestPayload(requestPayload);
    activityLog.setEndpoint(endPoint);
    return saveActivityLog(activityLog);
  }

  @Override
  public void updateActivityLog(ActivityLog activityLog, ResponseEntity<?> responseEntity) {
    try {
      String response = objectMapper.writeValueAsString(responseEntity.getBody());
      activityLog.setResponsePayload(response);
      activityLog.setResponseCode(responseEntity.getStatusCode().value());
      activityLog.setResponseOn(new Date());
      saveActivityLog(activityLog);
    } catch (JsonProcessingException e) {
      log.error("Error in while response parsing after feign call {}", e.getMessage());
    }
  }

  @Override
  public String getEventName(Integer eventValue) {
    if (eventValue != null) {
      return Arrays.stream(EventType.values())
          .filter(value -> value.getEventValue() == eventValue)
          .toList()
          .get(0)
          .getEventName();
    }
    return null;
  }
}
