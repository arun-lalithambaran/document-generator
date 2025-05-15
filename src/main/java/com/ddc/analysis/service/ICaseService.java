package com.ddc.analysis.service;

import com.ddc.analysis.entity.*;
import com.ddc.analysis.entity.PlateRunData;
import com.ddc.analysis.enums.CaseProgressStatus;
import com.ddc.analysis.enums.CaseStatus;
import com.ddc.analysis.pi.entities.Report;
import com.ddc.analysis.pi.entities.ReportMarker;
import com.ddc.analysis.pi.entities.ReportResultMaster;
import com.ddc.analysis.pi.entities.TestPartyRole;
import com.ddc.analysis.pi.projection.MarkerPIProjection;
import com.ddc.analysis.pi.request.CaseChangeActionRequest;
import com.ddc.analysis.projection.CaseNumberAndCaseIdProjection;
import com.ddc.analysis.qc.entities.QCCaseValidation;
import com.ddc.analysis.request.*;
import com.ddc.analysis.response.*;
import com.ddc.analysis.response.assertion.AccessionCase;
import com.ddc.analysis.response.assertion.AncCaseStatisticsResponse;
import com.ddc.analysis.response.assertion.CaseCommentPageResponse;
import com.ddc.analysis.response.assertion.SchedulingAgencyResponse;
import jakarta.transaction.Transactional;

import java.util.*;

import jcifs.smb.SmbFile;
import org.jetbrains.annotations.NotNull;
import org.springframework.http.ResponseEntity;

/** Created by aparna.prakash ON 18-10-2021 */
public interface ICaseService {
  CasePageResponse filterCases(String filter);

  CasePageResponse getDedicatedCaseList(String filter, List<Integer> caseStatusIds);

  NotaryMasterDetails getNotaryMasterDetails();

  /*To get all cases linked to the given case id*/
  List<Cases> getAllLinkedCasesInAnr(Long caseId, Boolean includeClosedCases);

  void assignUserToAllLinkedCases(List<Cases> linkedCaseList, Long userId, UserMaster loggedUser);

  List<CaseDetailResponse> getCaseDetails(Long caseId);

  void assignCaseToUser(Long caseId, Long userId, UserMaster loggedUser);

  Cases getCase(Long caseId);

  Cases changeCaseProgressStatus(
      Long caseId, CaseProgressStatus caseProgressStatus, UserMaster user);

  Cases changeCaseStatus(Cases caseData, String caseStatus, UserMaster user);

  void updateCaseDetailsFromANC(Long caseNumber);

  List<Long> getCaseQCValidationIds(List<QCCaseValidation> qcValidations);

  AlertResponse getAllAlertOfCase(Long caseId, Boolean isGroupId);

  Cases addAcknowledgementOfAlert(Long alertId, UserMaster user);

  Cases updateCaseStatusToPHDReview(Cases aCase, CaseStatus caseStatus, UserMaster user);

  List<CasePHDResponse> updateCaseStatusToPhdReviewAsGroup(Long caseId, UserMaster user);

  List<CasePHDResponse> updateCaseStatusToSupervisorReviewAsGroup(Long caseId, UserMaster user);

  List<CasePHDResponse> updateCaseStatusToSignatureReadyAsGroup(Long caseId, UserMaster user);

  void logCommunication(
      Cases aCase, CaseCommunicationRequest caseCommunicationRequest, UserMaster user);

  List<CaseCommunication> getCaseCommunicationLogsByCase(Cases aCase);

  CaseStatusChangelog saveCaseHistory(Long caseId, UserMaster user, String caseStatus);

  List<CaseStatusChangelog> getCaseHistory(Cases aCase);

  CaseApproval approvalOfCaseReport(CaseApproval caseApproval);

  CaseApproval getCaseApprovalById(Integer caseApprovalId);

  void rejectCaseReport(CaseApproval caseApproval, UserMaster user, Cases aCase);

  Optional<CaseApproval> getCaseApprovalByCaseAndReport(Cases aCase, Report report);

  Cases proceedToClose(Cases aCase, boolean isCaseClose, UserMaster user);

  void assignToDataAnalyst(Cases aCase, UserMaster user);

  List<CaseSampleMapping> getSamplesFromCase(Long caseId);

  CaseDetailResponse addReportInformationToCaseDetailResponse(
      Cases caseData, CaseDetailResponse caseDetailResponse, Report report, boolean flag);

  Double formatCpiValue(Report report);

  Double formatCpi(Double cpi);

  Double formatResidualCpiValue(Report report);

  List<LociResponse> buildLociResponses(
      List<ReportMarker> usedMarkerPIProjections,
      List<Samples> samples,
      boolean isUsed,
      String testTypeSubCategory);

  List<ReportMarker> filterYStrMarkers(
      Report report, List<ReportMarker> reportMarkers, String reportTestTypeSubCategory);

  PICalculationLabelResponse getCalculationLabel(
      TestTypeSubCategory testTypeSubCategory, Report report);

  Optional<Cases> getCaseByCaseNumber(String caseNumber);

  /*
   * Creating roles list using each test type
   *
   * @param samples
   * @param title
   */
  void removingUnMatchingSamples(List<Samples> samples, String title);

  CaseDetailResponse buildCaseDetailResponse(
      Long caseId,
      CaseDetailResponse caseDetailResponse,
      TestTypeSubCategory testTypeSubCategory,
      CaseResponse caseResponse,
      List<Samples> samples,
      Report report);

  List<SnpCaseDetail> getSnpCaseDetails(Cases caseData);

  String getNippProbability(NippCaseDetails nippCaseDetails);

  ReportResultMaster nippCaseResult(String resultText);

  String nippFetusGender(String genderText);

  List<LociResponse> removeAmelIfTestTypeIsPrenatel(
      String testTypeSubCategory, List<LociResponse> activeLoci);

  CaseDetailResponse getCaseDetailsOfReport(Report report);

  CaseChangeActionResponse getLatestCaseChangeActionOfCase(Cases caseData);

  Boolean isAmendedCase(Cases aCase);

  Boolean isCaseDefinitionUpdatedCase(Cases aCase);

  Boolean isReprintedCase(Cases aCase);

  List<IndicatorMaster> getIndicatorsOfCaseId(Long caseId);

  void addIndicatorResponseInCaseDetailsResponse(Cases caseData, CaseResponse caseResponse);

  void createAllRDO(Long caseNumber, String regenerationAction);

  List<CaseChangeActionResponse> getCaseChangeActionDetails(Cases caseData);

  void setOriginalCaseStatus(Cases aCase);

  void reconstructBasedStatusChange(Cases caseData, Report report);

  void updateLinkedCaseStatus(Long caseId, Long sampleId, String status);

  void saveToCaseReviewLog(Cases caseData, String caseReviewType, UserMaster user);

  Boolean updateSignOutDate(UserMaster user, Cases aCase, Date date, Long faultToleranceId);

  Boolean updateCaseDueDate(
      UserMaster user, List<Long> groupIdList, Date caseDueDate, Long faultToleranceId);

  Optional<CaseReviewLog> getCaseReviewLog(Cases caseData, String reviewType);

  Boolean updateCaseStatusToAccessioning(
      Cases aCase, UserMaster user, long parsedCaseNumber, String slug, Long faultToleranceId);

  void updateCase(CaseChangeActionRequest caseChangeActionRequest);

  Report createCopyOfReport(Report report);

  /*use as an inverted method*/
  boolean isOnHoldOrCancelled(CaseStatus caseStatus);

  Agency getAgency(AccessionCase caseDetails);

  void saveCaseChangeRequest(Cases caseData, CaseChangeActionRequest caseChangeActionRequest);

  List<CaseIndicatorMapping> getIndicatorsOfCaseIdAndSlug(Long caseId, String slug);

  List<Samples> getViableSamplesFromCase(Cases caseData);

  CaseSampleMapping getCaseSampleMapping(Cases aCase, Samples source);

  List<CaseSampleMapping> getCaseSampleMappingByPlateAndSampleAndViable(
      Samples sample, Plates plate, boolean viableStatus);

  List<CaseNumberAndCaseIdProjection> getCaseNumberAndCaseIdBySampleAndViabilityCheckIsTrue(
      Samples sample);

  void updateTestPartyRoleNameResponse(Report report, List<Samples> samples);

  TestTypeSetting getTestTypeSetting(
      String settingFor,
      TestTypeSubCategory testTypeSubCategory,
      TestTypeSubCategory caseTestTestType);

  ResponseEntity<SchedulingAgencyResponse> getAgencyDetails(Integer agencyId);

  List<CaseSampleMapping> getCaseSampleMappingByPlateAndSampleAndViabilityCheckRequiredTrue(
      Samples sample, Plates plate);

  List<CaseSampleMapping> getCaseSampleMappingByPlateAndViabilityCheckRequiredTrue(Plates plate);

  Boolean getViableStatus(Cases aCase, Samples sample);

  List<CaseSampleMapping> getCaseSampleMappingByPlateAndViabilityCheckIsTrueAndViableIsNotTrue(
      Plates plate);

  void updateHaploIndex(Cases aCase, List<Report> reports, Double haploGroupIndex);

  List<String> findNataAndSccIndicatorsOfCase(Long caseId);

  // NOteList
  List<SamplePlateRunMapping> findBySampleSampleId(Long sampleId);

  List<CaseCheckListResponse> getCheckListDetailsOfACase(Long caseId);

  void submitCheckList(String caseCheckListRequest);

  Cases sendToPHDPrimaryReview(Cases caseId, UserMaster user);

  Cases withdrawToPHDPrimaryReview(Cases caseId, UserMaster user);

  List<LociResponse> getExcludedActiveLociOfCaseInReport(Report report);

  boolean hasIndicatorToPhdReview(Cases aCase);

  boolean hasIndicatorToSupervisorReview(Cases aCase);

  String ambBlankValueForDifferentRuns(List<PlateRunData> plateRunData);

  Cases sendToPHDSupervisorReview(Cases aCase, UserMaster user);

  List<CaseReportExportResponse> getActiveLociOfCaseToExport(Long caseId);

  void updateCaseLabNotes(Cases aCase, CaseLabNotesRequest caseLabNotesRequest, UserMaster user);

  List<MarkerPIProjection> getActiveLociOfCase(Long caseId, Long reportId);

  List<Samples> findSamplesByCaseAndViableStatus(Cases aCase, boolean viableStatus);

  CaseCommentPageResponse getAncCaseComments(
      String caseNumber, Integer reqType, Integer pageNo, Integer pageSize);

  Cases getCaseDataByCaseNumber(String caseNumber);

  List<AccessionCase> getAccessionCases(
      Long count, List<String> caseNumberList, Long faultToleranceId, Long caseNumber);

  List<String> getLinkedCaseNumbersFromAccessioning(Long groupId);

  List<Long> fetchAllLinkedCasesOfCaseNumberList(List<String> caseNumberList);

  void deleteFromCaseReviewLog(Cases caseData, String caseReviewType, UserMaster user);

  void updateCaseStatusFromAnc(List<String> caseNumber);

  boolean hasNataOrrSccIndicator(Cases aCase);

  void updateCaseApprovals(Cases aCase);

  CaseStatusChangelog getCaseHistoryModifiedDateForCaseWithStatus(Cases aCase, String caseStatus);

  @Transactional
  void tagCase(Long caseId, Long userId, Integer tagAction);

  boolean getTag(Cases aCase, Long userId);

  List<String> getTaggedUserOfCase(Cases aCase);

  List<String> getAllLinkedCaseNumbers(Long groupId);

  void withdrawLinkedCasesToPHDPrimaryReview(Long caseId, UserMaster user);

  void updateCaseGroupStatus(
      Cases aCase, boolean isStatusOverride, UserMaster user, String caseChangeAction);

  void syncLinkedCaseStatus(Cases aCase, UserMaster user);

  boolean isLinkedCase(Cases aCase);

  boolean isCaseClosed(Cases aCase);

  void syncHelpTicketUpdates(Cases aCase, List<Integer> action);

  void logDelayCaseHistory(List<Cases> caseList, Date caseDueDate, UserMaster user);

  DelayCaseHistoryPageResponse findAllDelayCaseHistory(String filter);

  List<CaseMinimalResponse> getDelayCaseMasterData();

  List<String> getLinkedCasesOfSample(Long caseId, Long sampleId);

  void closeCasesAsGroup(
      Long caseId, List<Long> selectedReportIds, boolean isCaseClose, UserMaster user);

  Boolean overrideCaseStatus(CaseStatus caseStatus, Long caseId);

  void bulkCaseSign(List<Long> caseIds, UserMaster user);

  void assignToNotaryQueue(List<Long> caseIds, UserMaster notaryUser, UserMaster user);

  void applyNotarySeal(NotarySealAndCloseCasePayload payload, UserMaster user);

  @org.springframework.transaction.annotation.Transactional
  void closeCaseGroup(List<Long> caseIds, UserMaster user);

  void updateRaceForPiCalc(Long caseId, Long reportId, String race, UserMaster user);

  Map<Long, TestPartyRole> getSampleIdTestPartyRoleMap(List<CaseSampleMapping> caseSampleMappings);

  void saveNippCaseData(
      String caseNumber, NippCaseData nippCaseData, String fileName, Plates plate);

  @NotNull
  Optional<NippCaseData.PaternityTest> getPaternityTestResultBasedOnReportRace(
      List<NippCaseData.PaternityTest> resultList, String race);

  NippCaseQcResponse getNippCaseQcResponse(Long caseId);

  List<ScatterPlotFileListResponse> getScatterPlotFileList(
      String nippSampleId, String caseNumber, Long sequenceNumber);

  @NotNull
  String getBasePathForScatterPlotsByCaseNumber(String caseNumber, Long sequenceNumber);

  String getFileContentByName(String fileName, String caseNUmber, Long sequenceNumber);

  List<CaseStatistics> getDashboardDetails(Date startDate, Date endDate);

  AncCaseStatisticsResponse.AncCaseStatistics getAncDashboardCaseCountData(
      String startDate, String endDate);

  DashboardCaseResponse getDashboardCaseList(String filter);

  DashboardCaseResponse getAncDashboardCaseList(String filter);

  byte[] getDataFromSourceFile(SmbFile sourceFile);

  List<TranslationResponse.Translation> setTranslationResponse(
      List<CaseReportLangugae> caseReportLanguageList, Cases aCase);

  void overrideFetusGenderNipp(Long caseId, String fetusGender);

  Object getPaternityQuestionnaire(Long caseNumber);

  void sendCaseClosingReportAsAsync(Set<Long> caseId, UserMaster user);

  void updateCaseStatusAndReAssign(Long caseId, Integer statusId, UserMaster user);

  void setActivePlateRunFromCase(Long plateId, Long caseId, LoggedUser loggedUser);
  void updateUnderReviewStatus(Long caseId, Integer statusId, LoggedUser loggedUser);
  List<RsData> getAllRsData();
}
