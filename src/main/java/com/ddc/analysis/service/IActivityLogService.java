package com.ddc.analysis.service;

import com.ddc.analysis.entity.ActivityLog;
import com.ddc.analysis.entity.UserMaster;
import org.springframework.http.ResponseEntity;

/** Created by sujith.g ON 08-12-2021 */
public interface IActivityLogService {

  ActivityLog saveActivityLog(ActivityLog activityLog);

  ActivityLog createActivityLog(
          StackTraceElement stackTraceElement, UserMaster user, String requestPayload, String endPoint);

  void updateActivityLog(ActivityLog activityLog, ResponseEntity<?> responseEntity);
  String getEventName(Integer eventValue);
}
