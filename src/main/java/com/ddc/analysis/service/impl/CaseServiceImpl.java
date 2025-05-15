package com.ddc.analysis.service.impl;

import static com.ddc.analysis.enums.CaseProgressStatus.UN_ASSIGNED;
import static com.ddc.analysis.enums.IntegrationApiMasters.*;
import static com.ddc.analysis.pi.constants.PIConstants.*;
import static com.ddc.analysis.pi.constants.PIConstants.AMELOGENIN;
import static com.ddc.analysis.pi.constants.PIConstants.CHILD;
import static com.ddc.analysis.pi.constants.PIConstants.MOTHER;
import static com.ddc.analysis.qc.constants.QCConstants.*;
import static com.ddc.analysis.util.AppConstants.*;
import static com.ddc.analysis.util.DateUtils.getEndOfTheDay;
import static com.ddc.analysis.util.DateUtils.getStartOfTheDay;
import static java.text.MessageFormat.format;
import static org.springframework.data.domain.Sort.Direction.ASC;
import static org.springframework.data.domain.Sort.Direction.DESC;

import com.ddc.analysis.component.*;
import com.ddc.analysis.config.SambaConfigProperties;
import com.ddc.analysis.config.SambaConfiguration;
import com.ddc.analysis.converter.*;
import com.ddc.analysis.entity.*;
import com.ddc.analysis.entity.PlateRunData;
import com.ddc.analysis.enums.*;
import com.ddc.analysis.enums.CaseStatus;
import com.ddc.analysis.exception.*;
import com.ddc.analysis.feign.AccessioningCaseCommentFeignClient;
import com.ddc.analysis.feign.AccessioningCaseFeignClient;
import com.ddc.analysis.feign.SchedulingCaseFeignClient;
import com.ddc.analysis.pi.constants.PIConstants;
import com.ddc.analysis.pi.entities.*;
import com.ddc.analysis.pi.projection.CaseTestTypeProjection;
import com.ddc.analysis.pi.projection.MarkerPIProjection;
import com.ddc.analysis.pi.projection.ReportMarkerInfo;
import com.ddc.analysis.pi.repository.*;
import com.ddc.analysis.pi.request.CaseChangeActionRequest;
import com.ddc.analysis.pi.service.IAdditionalCalculationService;
import com.ddc.analysis.pi.service.ICPIService;
import com.ddc.analysis.pi.service.IReportService;
import com.ddc.analysis.pi.service.PIService;
import com.ddc.analysis.pi.service.impl.PICalculationFactoryImpl;
import com.ddc.analysis.pi.service.impl.ReportTemplateServiceImpl;
import com.ddc.analysis.pi.util.PIHelper;
import com.ddc.analysis.pi.util.TemplateConstants;
import com.ddc.analysis.projection.CaseNumberAndCaseIdProjection;
import com.ddc.analysis.projection.CaseStatusCountProjection;
import com.ddc.analysis.projection.CaseTestTypeCountProjection;
import com.ddc.analysis.projection.TranslationProjection;
import com.ddc.analysis.qc.entities.QCCaseValidation;
import com.ddc.analysis.qc.entities.QCCaseValidationAlert;
import com.ddc.analysis.qc.enums.ValidationStatus;
import com.ddc.analysis.qc.factory.CaseQCFactory;
import com.ddc.analysis.qc.repository.QCCaseValidationAlertRepository;
import com.ddc.analysis.qc.repository.QCCaseValidationRepository;
import com.ddc.analysis.qc.repository.QCValidationRepository;
import com.ddc.analysis.qc.request.CaseQCRequest;
import com.ddc.analysis.qc.services.CaseMetricFailureQc;
import com.ddc.analysis.qc.services.ConcordanceFailureNippQc;
import com.ddc.analysis.qc.services.PossibleSampleSwitchNippQc;
import com.ddc.analysis.repository.*;
import com.ddc.analysis.repository.specification.CaseSpecification;
import com.ddc.analysis.repository.specification.DelayCaseHistorySearchRequest;
import com.ddc.analysis.repository.specification.DelayCaseHistorySpecification;
import com.ddc.analysis.request.*;
import com.ddc.analysis.response.*;
import com.ddc.analysis.response.assertion.*;
import com.ddc.analysis.service.*;
import com.ddc.analysis.util.*;
import com.ddc.analysis.webclient.AccessionWebClient;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import jcifs.smb.SmbException;
import jcifs.smb.SmbFile;
import jcifs.smb.SmbFileInputStream;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

/** Created by aparna.prakash ON 18-10-2021 */
@RefreshScope
@Slf4j
@Service
public class CaseServiceImpl implements ICaseService {
  @Autowired private PlateRunDataRepository plateRunDataRepository;
  @Autowired private AgencyRepository agencyRepository;
  @Autowired private RedrawDataRepository redrawDataRepository;
  @Autowired private ReportResultMasterRepository reportResultMasterRepository;
  @Autowired private PossibleSampleSwitchNippQc possibleSampleSwitchNippQc;
  @Autowired private CaseMetricFailureQc caseMetricFailureQc;
  @Autowired private ConcordanceFailureNippQc concordanceFailureNippQc;
  @Autowired private CaseQCFactory caseQCFactory;
  @Autowired JdbcTemplate jdbcTemplate;

  @Autowired FileRetriever fileRetriever;

  @Autowired private TemplateMasterFieldRepository templateMasterFieldRepository;
  @Autowired CacheManager cacheManager;
  @Autowired private ReportInterpretationRepository reportInterpretationRepository;

  @Autowired private RsMasterDataRepository rsMasterDataRepository;

  private static final String UPDATE_URL = "/updateCaseDetails";

  @Autowired private SambaConfigProperties sambaConfigProperties;

  @Autowired private FileLoadInfoRepository fileLoadInfoRepository;

  private final Executor asyncExecutor;

  public CaseServiceImpl(@Qualifier("asyncExecutor") Executor asyncExecutor) {
    this.asyncExecutor = asyncExecutor;
  }

  @Value("${ddc.accessioning.case.status}")
  public String updateCaseStatus;

  @Value("${ddc.accessioning.url}")
  public String accessioningBaseURL;

  @Value("${ddc.azure.connection.base.url}")
  public String azureConnectionBaseURL;

  @Value("${ddc.azure.token}")
  public String getToken;

  @Value("${ddc.accessioning.case.details}")
  public String caseDetailURL;

  @Value("${ddc.accessioning.case.list}")
  public String caseListURL;

  @Autowired SambaConfiguration sambaConfiguration;
  @Autowired HttpServletRequest request;
  @Autowired private CasesRepository caseRepo;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private ErrorMessage errorMessage;
  @Autowired private CasePageResponseConverter casePageResponseConverter;
  @Autowired private IUserService userService;
  @Autowired private CaseSampleMappingRepository caseSampleMappingRepository;
  @Autowired private CaseToCaseResponseConverter caseToCaseResponseConverter;
  @Autowired private SampleToSampleResponseConverter sampleToSampleResponseConverter;
  @Autowired private SampleMarkersRepository sampleMarkerRepository;
  @Autowired private QCCaseValidationRepository qcCaseValidationRepo;
  @Autowired private CaseAlertResponseConverter caseAlertResponseConverter;
  @Autowired private QCCaseValidationAlertRepository qcCaseValidationAlertRepo;
  @Autowired private IReportService reportService;
  @Autowired private CaseCommunicationRepository caseCommunicationLogsRepository;
  @Autowired private CaseStatusChangelogRepository caseStatusChangelogRepository;
  @Autowired private CaseStatusRepository caseStatusRepository;
  @Autowired private CaseApprovalRepository caseApprovalRepository;
  @Autowired private TestTypeSettingRepository testTypeSettingRepository;
  @Autowired private CaseIndicatorMappingRepository caseIndicatorMappingRepository;
  @Autowired private IndicatorResponseConverter indicatorResponseConverter;
  @Autowired private AccessioningCaseFeignClient accessioningFeignClient;
  @Autowired private SchedulingCaseFeignClient schedulingCaseFeignClient;
  @Autowired private ITestTypeService testTypeService;
  @Autowired private LabVantageIntegrationServiceImpl labVantageIntegrationService;
  @Autowired private PICalculationFactoryImpl piCalculationFactory;
  @Autowired private CaseChangeRequestRepository caseChangeRequestRepository;
  @Autowired private CaseReviewLogRepository caseReviewLogRepository;
  @Autowired private IActivityLogService activityLogService;
  @Autowired private CaseReportLangugaeRepository caseReportLanguageRepository;
  @Autowired private PIService piService;
  @Autowired private CaseCheckListMappingRepository caseCheckListMappingRepository;
  @Autowired private SamplePlateRunMappingRepository samplePlateRunRepository;
  @Autowired private GeneralSettingRepository generalSettingsRepository;
  @Autowired private QCValidationRepository qcValidationRepository;
  @Autowired private SamplesRepository sampleRepository;
  @Autowired private TestTypeSubCategoryRepository testTypeSubCategoryRepository;
  @Autowired private IDataImportService dataImportService;
  @Autowired private IAdditionalCalculationService additionalCalculationService;
  @Autowired private AdditionalCalcSupportComponent additionalCalcSupportComponent;
  @Autowired private ReportHistoryRepository reportHistoryRepository;
  @Autowired private PISupport piSupport;
  @Autowired private RequestSession requestSession;
  @Autowired private IntegrationFaultToleranceRepository integrationFaultToleranceRepository;
  @Autowired private AccessionWebClient accessionWebClient;
  @Autowired private DelayCaseHistoryRepository delayCaseHistoryRepository;
  @Autowired private DelayCaseHistorySpecification delayCaseHistorySpecification;
  @Autowired PlateSampleFileLoadMappingRepository plateSampleFileLoadMappingRepository;
  @Autowired ReportMarkerRepository reportMarkerRepository;
  @Autowired SampleReProcessRequestRepository sampleReProcessRequestRepository;
  @Autowired private ReportTranslationRepository reportTranslationRepository;
  @Autowired private ReportToClosingRepository reportToClosingRepository;
  @Autowired private ReportTemplateServiceImpl reportTemplateService;
  @Autowired private EntityManager entityManager;
  @Autowired private TestPartyRoleRepository testPartyRoleRepository;
  @Autowired private IPlateQCProcessService plateQCProcessService;
  @Autowired private CaseSampleNippQCMappingRepository caseSampleNippQCMappingRepository;
  @Autowired private NippCaseDetailsRepository nippCaseDetailsRepository;
  @Autowired private ScheduleJobServiceImpl scheduleJobService;
  @Autowired private NippCaseCalcSheetRepository nippCaseCalcSheetRepository;

  @Autowired
  private TestTypeSubCategoryMarkerMappingRepository testTypeSubCategoryMarkerMappingRepository;

  @Autowired private CaseTagMappingRepository caseTagMappingRepository;

  @Autowired
  private CaseCheckListMappingToCaseCheckListResponseConverter
      caseCheckListMappingToCaseCheckListResponseConverter;

  @Autowired
  private ReportResultToReportResultResponseConverter reportResultToReportResultResponseConverter;

  @Autowired private AzureTokenUtils azureTokenUtils;
  @Autowired private ISampleService sampleService;
  @Autowired private ICPIService cpiService;
  @Autowired private IPlateService plateService;
  @Autowired private PIHelper piHelper;
  @Autowired private AccessioningCaseCommentFeignClient accessioningCaseCommentFeignClient;
  @Autowired private IIntegrationFaultTolerance integrationFaultTolerance;
  @Autowired private IIntegrationFaultTolerance faultTolerance;
  @Autowired private FirebaseMessagingService firebaseMessagingService;

  @Autowired private DelayCaseHistoryConverter delayCaseHistoryConverter;

  @Autowired SampleReProcessFilterMasterResponseConverter minimalResponseConverter;

  @Autowired TestPartyRaceRepository testPartyRaceRepository;

  @Autowired ReportSampleRepository reportSampleRepository;
  @Autowired private SuccessMessage successMessage;
  @Autowired private ReportRepository reportRepository;

  @Autowired private GeneralSettingRepository generalSettingRepository;

  @Autowired private SnpSampleDetailsRepository snpSampleDetailsRepository;

  @Autowired private RsGenotypeDataRepository rsGenotypeDataRepository;
  private Boolean isApiSuccess = false;

  @Value("${ddc.scheduling.url}")
  private String schedulingEndPoint;

  /**
   * to filter case based on userid
   *
   * @param filter
   * @return CasePageResponse
   */
  @Override
  public CasePageResponse filterCases(String filter) {
    String decode = URLDecoder.decode(filter, StandardCharsets.UTF_8);
    try {
      CaseFilterRequest caseFilterRequest = objectMapper.readValue(decode, CaseFilterRequest.class);
      int pageNumber = caseFilterRequest.getPageNumber();
      // Frontend starts page from 1
      pageNumber = pageNumber - 1;
      Pageable pageable =
          PageRequest.of(
              pageNumber,
              caseFilterRequest.getPageSize(),
              Sort.by(ASC, Cases_.CURRENT_DUE_DATE).and(Sort.by(ASC, Cases_.CASE_NUMBER)));
      CaseFilterDataResponse caseFilterData = getCaseFilterData(caseFilterRequest);
      CaseSearchRequest caseSearchRequest =
          buildCaseSearchRequest(caseFilterRequest, caseFilterData);
      if (caseFilterRequest.getIsMyCaseTab() == Boolean.TRUE) {
        caseSearchRequest.setCaseStatusesNotIn(
            List.of(CaseStatus.SIGNATURE_QUEUE.ordinal(), CaseStatus.NOTARY_QUEUE.ordinal()));
      } else {
        caseSearchRequest.setCaseStatusesNotIn(List.of());
      }
      Page<Cases> cases = caseRepo.findAll(CaseSpecification.getCases(caseSearchRequest), pageable);
        return casePageResponseConverter.convert(cases);
    } catch (JsonProcessingException e) {
      throw new BadRequestException(errorMessage.getInvalidRequest());
    }
  }

  /**
   * Case filter
   *
   * @param caseFilterRequest
   * @return CaseFilterDataResponse
   */
  private CaseFilterDataResponse getCaseFilterData(CaseFilterRequest caseFilterRequest) {
    // Convert String to CaseStatus
    List<CaseStatus> caseStatuses = null;
    if (caseFilterRequest.getCaseStatuses() != null
        && !caseFilterRequest.getCaseStatuses().isEmpty()) {
      caseStatuses =
          caseFilterRequest.getCaseStatuses().stream()
              .map(CaseStatus::getCaseStatus)
              .collect(Collectors.toList());
    } else {
      caseStatuses = new ArrayList<>(EnumSet.allOf(CaseStatus.class));
      caseStatuses.remove(CaseStatus.NOT_READY);
    }
    // Convert to CaseProgressStatus
    List<CaseProgressStatus> caseProgressStatuses = null;
    if (caseFilterRequest.getCaseProgressStatuses() != null
        && !caseFilterRequest.getCaseProgressStatuses().isEmpty()) {
      caseProgressStatuses =
          caseFilterRequest.getCaseProgressStatuses().stream()
              .map(CaseProgressStatus::getStatus)
              .collect(Collectors.toList());
    } else {
      caseProgressStatuses = new ArrayList<>(EnumSet.allOf(CaseProgressStatus.class));
    }
    // convert to CasePriority
    List<CasePriority> casePriorities = null;
    if (caseFilterRequest.getCasePriorities() != null
        && !caseFilterRequest.getCasePriorities().isEmpty()) {
      casePriorities =
          caseFilterRequest.getCasePriorities().stream()
              .map(CasePriority::getCasePriority)
              .collect(Collectors.toList());
    } else {
      casePriorities = new ArrayList<>(EnumSet.allOf(CasePriority.class));
    }
    return CaseFilterDataResponse.builder()
        .caseStatuses(caseStatuses)
        .caseProgressStatuses(caseProgressStatuses)
        .casePriorities(casePriorities)
        .build();
  }

  /**
   * Assign case to User
   *
   * @param caseId
   * @param userId
   * @param loggedUser
   */
  @Override
  public void assignCaseToUser(Long caseId, Long userId, UserMaster loggedUser) {
    UserMaster user = userService.getUser(userId);
    Cases caseData = getCase(caseId);

    if (!userService.isUserInParticularRole(user, SUPERVISOR_ROLE)
        && caseData.getCaseStatus() == CaseStatus.SUPERVISOR_REVIEW) {
      throw new BadRequestException(errorMessage.getCantAssignCase());
    }

    /*if the same user has already assigned case,
    we automatically unassigned(except cases with ready for signature) him from that case and reassign to new case.
    if the case is linked case the byCaseAssigneeList will have multiple cases in that scenario,
    a check is done to see if the linked case list contains the current case that the user chose, and only
    if the current case is not in the linked case list do we remove the user from the previous cases, this is to group linked cases*/
    List<Cases> byCaseAssigneeList = caseRepo.findByCaseAssignee(user);
    byCaseAssigneeList.stream()
        .filter(
            aCase ->
                (aCase.getCaseStatus() != CaseStatus.SIGNATURE_QUEUE
                    && aCase.getCaseStatus() != CaseStatus.NOTARY_QUEUE))
        .forEach(
            cases -> {
              List<Cases> linkedCasesList = getAllLinkedCasesInAnr(cases.getCaseId(), false);
              if (!byCaseAssigneeList.isEmpty() && !linkedCasesList.contains(caseData)) {
                cases.setCaseAssignee(null);
                // delete signed date and send to ANC
                if (!isCaseClosed(cases)) {
                  updateSignOutDate(user, cases, null, null);
                }
                cases.setCaseProgressStatus(UN_ASSIGNED);
                caseRepo.save(cases);
                saveCaseHistory(cases.getCaseId(), user, UN_ASSIGNED.getStatus());
              }
            });

    caseData.setCaseAssignee(user);
    if (caseData.getCaseStatus() == CaseStatus.SIGNATURE_QUEUE) {
      caseData.setCaseStatus(CaseStatus.PHD_REVIEW);
    }
    caseData.setCaseProgressStatus(CaseProgressStatus.IN_PROGRESS);
    List<Report> reports = reportService.getReportsByParentIsNull(caseData);
    reconstructBasedStatusChange(caseData, reports.stream().findFirst().orElse(null));
    caseRepo.save(caseData);
    if (caseData.getCaseStatus() == CaseStatus.PHD_REVIEW
        || caseData.getCaseStatus() == CaseStatus.PHD_SUPERVISOR_REVIEW) {
      try {
        long caseNumber = Long.parseLong(caseData.getCaseNumber());
        updateCaseStatusToAccessioning(caseData, user, caseNumber, ANLYSIS_INPROGRESS, null);
      } catch (NumberFormatException e) {
        throw new BadRequestException(errorMessage.getCaseNumberShouldContainNumericValues());
      }
    }
    saveCaseHistory(caseId, user, CaseProgressStatus.IN_PROGRESS.getStatus());

    // for unsigning if the report was signed while unsigning him from the current case
    deleteFromCaseReviewLog(caseData, CASE_REVIEW_TYPE_PHD, user);
    deleteFromCaseReviewLog(caseData, CASE_REVIEW_TYPE_NOTARY, user);

    // for clearing approvals on the case while un-assigning case
    if (caseData.getCaseStatus() != CaseStatus.MOVED_TO_CLOSE) {
      updateCaseApprovals(caseData);
    }
  }

  private Boolean isClosed(CaseStatus caseStatus) {
    return caseStatus == CaseStatus.CASE_CLOSED;
  }

  @Override
  public void assignUserToAllLinkedCases(
      List<Cases> linkedCaseList, Long userId, UserMaster loggedUser) {
    linkedCaseList.forEach(cases -> assignCaseToUser(cases.getCaseId(), userId, loggedUser));
  }

  /**
   * Get Case details from a specific case
   *
   * @param caseId
   * @return CaseDetailResponse
   */
  @Override
  public List<CaseDetailResponse> getCaseDetails(Long caseId) {
    Cases caseData = getCase(caseId);
    TestTypeSubCategory testType = caseData.getTestTypeSubCategory();
    /*for some reason once additional calculation is updated the original report from db cannot be fetched no
    matter what. this is a work around for it but with added technical debt*/
    List<Report> reports =
        reportRepository
            .findByIdIn(
                reportRepository.findReportIdByCaseIdAndParentIsNullAndIsActiveTrue(caseData))
            .stream()
            .filter(report -> report.getParent() == null && report.isActive())
            .toList();
    Optional<TestTypeSetting> dnaView =
        testTypeSettingRepository.findByTestTypeSubCategoryAndSettingForAndMetaKey(
            testType, DNA_VIEW_LABEL, META_KEY_PI_HAND_ENTERED);
    Optional<TestTypeSetting> multiReport =
        testTypeSettingRepository.findByTestTypeSubCategoryAndSettingForAndMetaKey(
            testType, MULTI_REPORT, MULTI_REPORT);
    return buildCaseDetailResponse(caseData, reports, testType, dnaView, multiReport);
  }

  /**
   * Creating Case details Response against each report
   *
   * @param caseData
   * @param reports
   * @param testTypeSubCategory
   * @param dnaView
   * @param multiReport
   * @return List<CaseDetailResponse>
   */
  private List<CaseDetailResponse> buildCaseDetailResponse(
      Cases caseData,
      List<Report> reports,
      TestTypeSubCategory testTypeSubCategory,
      Optional<TestTypeSetting> dnaView,
      Optional<TestTypeSetting> multiReport) {
    List<CaseDetailResponse> caseDetailResponses = new ArrayList<>();
    for (Report report : reports) {
      CaseDetailResponse caseDetailResponse = new CaseDetailResponse();
      Optional<CaseReviewLog> caseReviewLog = getCaseReviewLog(caseData, CASE_REVIEW_TYPE_PHD);
      caseDetailResponse.setReportSigned(caseReviewLog.isPresent());
      caseDetailResponse.setPiHandEntered(dnaView.isPresent());
      caseDetailResponse.setMultiReport(multiReport.isPresent());
      caseDetailResponse.setCommunicationExists(
          caseCommunicationLogsRepository.existsByCaseId(caseData));
      TestTypeSetting testTypeSettingForReportTitle =
          getTestTypeSetting(
              REPORT_TITLE, report.getTestTypeSubCategory(), caseData.getTestTypeSubCategory());
      if (multiReport.isPresent()) {
        String reportTestType = report.getTestTypeSubCategory().getTestTypeSubCategory();
        if (reportTestType.equals(Y_STR_WITH_RECONSTRUCT)) {
          caseDetailResponse.setTitle(Y_STR_COMPARISON);
        } else {
          caseDetailResponse.setTitle(reportTestType);
        }
      }
      caseDetailResponse.setCombinedCPI(report.getCombinedCPI());
      caseDetailResponse.setAutosomalCpi(report.getCalculatedCpi());
      caseDetailResponse.setCpiThresholdValue(
          Objects.requireNonNull(
                  generalSettingRepository.findByMetaKey(THRESHOLD_META_KEY).orElse(null))
              .getMetaValue());
      CaseResponse caseResponse = caseToCaseResponseConverter.convert(caseData);
      assert caseResponse != null;
      Optional<TestTypeSetting> optionalTestTypeSetting =
          testTypeSettingRepository.findByTestTypeSubCategoryAndSettingForAndMetaKey(
              report.getTestTypeSubCategory(),
              PIConstants.YSTR_HAPLO_GROUP_INDEX,
              PIConstants.YSTR);
      caseResponse.setBlockYSTRHaploGroupIndex(
          !(optionalTestTypeSetting.isPresent()
              || Boolean.TRUE.equals(report.getYstrTestPanelFound())));
      casePageResponseConverter.setCaseReviewLog(caseData, caseResponse);
      List<Samples> samples =
          getSamplesFromCase(caseData.getCaseId()).stream()
              .map(CaseSampleMapping::getSamples)
              .collect(Collectors.toList());

      updateTestPartyRoleNameResponse(report, samples);

      List<CaseSampleMapping> sampleMappings = getSampleMappingsFromCase(caseData.getCaseId());
      if (multiReport.isPresent())
        removingUnMatchingSamples(samples, caseDetailResponse.getTitle());
      List<SampleResponse> sampleResponses =
          sampleToSampleResponseConverter.convert(samples, caseData, sampleMappings, report);
      addSampleLevelIndicatorLinkedCasesAndRelatedCases(sampleResponses, caseData);
      caseResponse.setSamples(sampleResponses);
      addReportInformationToCaseDetailResponse(caseData, caseDetailResponse, report, false);
      List<Long> sampleIds =
          samples.stream().map(Samples::getSampleId).collect(Collectors.toList());
      List<Samples> activeSamples =
          sampleMappings.stream()
              .filter(
                  caseSampleMapping ->
                      caseSampleMapping.isActive()
                          && sampleIds.contains(caseSampleMapping.getSamples().getSampleId()))
              .map(CaseSampleMapping::getSamples)
              .collect(Collectors.toList());
      buildCaseDetailResponse(
          caseData.getCaseId(),
          caseDetailResponse,
          testTypeSubCategory,
          caseResponse,
          activeSamples,
          report);
      if (testTypeSettingForReportTitle != null
          && activeSamples.stream()
              .anyMatch(el -> el.getTestPartyRole().getRole().equalsIgnoreCase(FETUS))) {
        caseDetailResponse.setReportTypeId(
            Integer.valueOf(testTypeSettingForReportTitle.getMetaKey()));
        caseDetailResponse.setTitle(testTypeSettingForReportTitle.getMetaValue());
      }
      caseDetailResponses.add(caseDetailResponse);
      setYstrRelatedTestType(report, caseDetailResponse);
      addIndicatorResponseInCaseDetailsResponse(caseData, caseResponse);
      addReviewDetails(caseData, caseResponse);
      addAgencyDetails(caseData, caseDetailResponse);
    }
    return caseDetailResponses;
  }

  @Override
  public void updateTestPartyRoleNameResponse(Report report, List<Samples> samples) {
    Optional<Samples> allegedMother =
        samples.stream()
            .filter(s -> s.getTestPartyRole().getRole().equalsIgnoreCase(ALLEGED_MOTHER))
            .findFirst();
    if (allegedMother.isPresent()) {
      // no need of case test type
      TestTypeSetting motherRoleSetting =
          getTestTypeSetting(ALLEGED_MOTHER, report.getTestTypeSubCategory(), null);
      if (motherRoleSetting != null) {
        Optional<TestPartyRole> testPartyRoleOptional =
            testPartyRoleRepository.findById(Long.valueOf(motherRoleSetting.getMetaValue()));
        testPartyRoleOptional.ifPresent(tp -> allegedMother.get().setTestPartyRole(tp));
      }
    }
  }

  @Override
  public TestTypeSetting getTestTypeSetting(
      String settingFor,
      @NotNull TestTypeSubCategory reportTestType,
      TestTypeSubCategory caseTestTestType) {
    if (caseTestTestType == null) {
      Optional<TestTypeSetting> bySettingForAndReportTestType =
          testTypeSettingRepository.findBySettingForAndReportTestType(settingFor, reportTestType);
      return bySettingForAndReportTestType.orElse(null);
    }
    Optional<TestTypeSetting> bySettingForAndReportTestType =
        testTypeSettingRepository.findBySettingForAndReportTestTypeAndTestTypeSubCategory(
            settingFor, reportTestType, caseTestTestType);
    return bySettingForAndReportTestType.orElse(null);
  }

  private void setYstrRelatedTestType(Report report, CaseDetailResponse caseDetailResponse) {
    if (caseDetailResponse
        .getCaseResponse()
        .getTestTypeResponse()
        .getId()
        .equals(YSTR_WITH_RELEVANT_RECONSTRUCT_ID)) {
      Optional<TestTypeSetting> testTypeSettingForRelatedYstrTestType =
          testTypeSettingRepository.findByTestTypeSubCategoryAndSettingForAndMetaKey(
              report.getTestTypeSubCategory(),
              PIConstants.YSTR_RELATED_TEST_TYPE,
              PIConstants.RELATED_TEST_TYPE_META_KEY);
      if (testTypeSettingForRelatedYstrTestType.isPresent())
        caseDetailResponse.setYstrRelatedTestTypeId(
            testTypeSettingForRelatedYstrTestType.get().getReportTestType().getId());
    }
  }

  private void addAgencyDetails(Cases caseData, CaseDetailResponse caseDetailResponse) {
    if (caseData.getAgency() != null) {
      ResponseEntity<SchedulingAgencyResponse> agencyDetails =
          getAgencyDetails(caseData.getAgency().getAgencyId());
      if (agencyDetails != null) {
        SchedulingAgencyResponse agencyDetailsBody = agencyDetails.getBody();
        if (agencyDetailsBody != null) {
          caseDetailResponse.setAgencySettings(agencyDetailsBody.getData());
        }
      }
    }
  }

  public ResponseEntity<SchedulingAgencyResponse> getAgencyDetails(Integer agencyId) {
    String auth = azureTokenUtils.getAzureTokenOfOutBoundAPI(null);
    return schedulingCaseFeignClient.getAgencyDetails(auth, agencyId);
  }

  @Override
  public List<CaseSampleMapping> getCaseSampleMappingByPlateAndSampleAndViabilityCheckRequiredTrue(
      Samples sample, Plates plate) {
    return caseSampleMappingRepository
        .findByCaseSampleMappingBySampleAndPlateAndViabilityCheckIsTrue(sample, plate);
  }

  @Override
  public List<CaseSampleMapping> getCaseSampleMappingByPlateAndViabilityCheckRequiredTrue(
      Plates plate) {
    return caseSampleMappingRepository.findByCaseSampleMappingByPlateAndViabilityCheckIsTrue(plate);
  }

  @Override
  public Boolean getViableStatus(Cases aCase, Samples sample) {
    return caseSampleMappingRepository.findByViableStatusCaseDataAndSamples(aCase, sample);
  }

  @Override
  public List<CaseSampleMapping>
      getCaseSampleMappingByPlateAndViabilityCheckIsTrueAndViableIsNotTrue(Plates plate) {
    return caseSampleMappingRepository
        .findByCaseSampleMappingByPlateAndViabilityCheckIsTrueAndViableIsNotTrue(plate);
  }

  @Transactional
  @Override
  public void updateHaploIndex(Cases aCase, List<Report> reports, Double haploGroupIndex) {
    aCase.setYstrHaploGroupIndex(haploGroupIndex);
    caseRepo.save(aCase);
    reportService.saveCombinedIndex(reports, haploGroupIndex);
    piService.updateManualReportResult(aCase.getCaseId());
  }

  @Override
  public List<String> findNataAndSccIndicatorsOfCase(Long caseId) {
    ArrayList<String> nataSccSlugList = new ArrayList<>(Arrays.asList("NATA", "SCC"));
    return caseIndicatorMappingRepository.findNataAndSccIndicatorsOfCase(caseId, nataSccSlugList);
  }

  // NOteList
  @Override
  public List<SamplePlateRunMapping> findBySampleSampleId(Long sampleId) {
    return samplePlateRunRepository.findBySampleSampleId(sampleId);
  }

  @Override
  public List<CaseCheckListResponse> getCheckListDetailsOfACase(Long caseId) {
    List<CaseCheckListMapping> checkListMappings =
        caseCheckListMappingRepository.findByCaseIdCaseId(caseId);
    return caseCheckListMappingToCaseCheckListResponseConverter.convert(checkListMappings);
  }

  @Override
  public void submitCheckList(String caseCheckListRequest) {
    String decode = URLDecoder.decode(caseCheckListRequest, StandardCharsets.UTF_8);
    try {
      CaseCheckListSubmitRequest caseCheckListSubmitRequest =
          objectMapper.readValue(decode, CaseCheckListSubmitRequest.class);
      List<Long> caseCheckListIds = caseCheckListSubmitRequest.getCaseCheckListIds();
      if (!CollectionUtils.isEmpty(caseCheckListIds)) {
        List<CaseCheckListMapping> checkListMappings =
            caseCheckListMappingRepository.findAllById(caseCheckListIds);
        if (!checkListMappings.isEmpty()) {
          checkListMappings.forEach(
              caseCheckListMapping -> caseCheckListMapping.setCheckedStatus(Boolean.TRUE));
          caseCheckListMappingRepository.saveAll(checkListMappings);
        }
      }
    } catch (JsonProcessingException e) {
      throw new BadRequestException(errorMessage.getInvalidRequest());
    }
  }

  @Override
  @Transactional
  public Cases sendToPHDPrimaryReview(Cases aCase, UserMaster user) {
    aCase.setCaseProgressStatus(UN_ASSIGNED);
    aCase.setCaseAssignee(null);
    aCase.setModifiedUser(user);
    saveToCaseReviewLog(aCase, PRIMARY_PHD_REVIEW, user);
    saveCaseHistory(aCase.getCaseId(), user, CaseStatus.PHD_REVIEW.getStatus());
    List<Report> reports = reportService.getReportsByParentIsNull(aCase);
    reports.forEach(report -> changeCaseApprovalStatusToFalse(aCase, report));
    Cases cases = caseRepo.save(aCase);
    piSupport.setCaseNumber(cases.getCaseNumber());
    piSupport.setCaseUpdate(true);
    return cases;
  }

  @Override
  public Cases withdrawToPHDPrimaryReview(Cases aCase, UserMaster user) {
    aCase.setModifiedUser(user);
    aCase.setCaseStatus(CaseStatus.PHD_REVIEW);
    // for unsigning if the report was signed while unsigning him from the current case
    deleteFromCaseReviewLog(aCase, CASE_REVIEW_TYPE_PHD, user);
    deleteFromCaseReviewLog(aCase, CASE_REVIEW_TYPE_NOTARY, user);
    saveCaseHistory(aCase.getCaseId(), user, CaseStatus.PHD_REVIEW.getStatus());
    List<Report> reports = reportService.getReportsByParentIsNull(aCase);
    reconstructBasedStatusChange(aCase, reports.stream().findFirst().orElse(null));
    reports.forEach(report -> changeCaseApprovalStatusToFalse(aCase, report));
    return caseRepo.save(aCase);
  }

  private void changeCaseApprovalStatusToFalse(Cases aCase, Report report) {
    Optional<CaseApproval> optionalCaseApproval =
        caseApprovalRepository.findByCaseIdAndReport(aCase, report);
    if (optionalCaseApproval.isPresent()) {
      CaseApproval caseApproval = optionalCaseApproval.get();
      caseApproval.setIsPICApproved(false);
      caseApproval.setIsReportApproved(false);
      caseApproval.setIsInterpretationApproved(false);
      caseApproval.setIsResultApproved(false);
      caseApprovalRepository.save(caseApproval);
    }
  }

  @Override
  public List<LociResponse> getExcludedActiveLociOfCaseInReport(Report report) {
    Sort sort = Sort.by(Sort.Order.by("marker.sortOrder"));
    List<CaseSampleMapping> caseSampleMappings = getSamplesFromCase(report.getCaseId().getCaseId());
    // we only need father and child samples for find excluded locis
    List<Samples> sampleListOfChildAndAllegedFather =
        caseSampleMappings.stream()
            .filter(
                sample ->
                    sample.getTestPartyRole().getAcronym().equals(ALLEGED_FATHER_ACRONYM)
                        || List.of(CHILD_ACRONYM, CHILD_FETUS_ACRONYM)
                            .contains(sample.getTestPartyRole().getAcronym()))
            .map(CaseSampleMapping::getSamples)
            .toList();
    List<ReportMarker> usedMarkerPIProjections =
        sampleMarkerRepository.findExcludedMarkerAndPiByCaseIdAndIsUsed(
            report.getCaseId().getCaseId(), Boolean.TRUE, report.getId(), 1L);
    return buildLociResponses(
        usedMarkerPIProjections,
        sampleListOfChildAndAllegedFather,
        true,
        report.getTestTypeSubCategory().getTestTypeSubCategory());
  }

  private void addSampleLevelIndicatorLinkedCasesAndRelatedCases(
      List<SampleResponse> sampleResponses, Cases caseData) {
    if (!sampleResponses.isEmpty()) {
      for (SampleResponse sampleResponse : sampleResponses) {
        if (sampleResponse != null) {
          List<Cases> allLinkedCasesOfSample =
              caseSampleMappingRepository.getAllLinkedCasesOfSample(
                  caseData.getCaseId(), sampleResponse.getSampleId());
          sampleResponse.setLinkedCases(casePageResponseConverter.convert(allLinkedCasesOfSample));
          List<Long> relatedSamplesIdsOfParticularTestedPartyId =
              sampleService.getRelatedSamplesOfAParticularSampleBasedOnItsTestedPartyId(
                  sampleResponse.getSampleId(), sampleResponse.getTestedPartyId());
          List<Cases> allRelatedCasesOfSample =
              caseSampleMappingRepository.getAllRelatedCasesOfSample(
                  caseData.getCaseId(), relatedSamplesIdsOfParticularTestedPartyId);
          sampleResponse.setRelatedCases(
              casePageResponseConverter.convert(allRelatedCasesOfSample));
          List<CaseIndicatorMapping> indicatorsOfCaseSample =
              caseIndicatorMappingRepository.findByCaseIdCaseIdAndSampleIdSampleId(
                  caseData.getCaseId(), sampleResponse.getSampleId());
          setAmbBlankDetailsOfCaseSample(caseData, sampleResponse);
          if (!indicatorsOfCaseSample.isEmpty()) {
            List<IndicatorMaster> indicatorMasters =
                indicatorsOfCaseSample.stream().map(CaseIndicatorMapping::getIndicatorId).toList();
            if (!indicatorMasters.isEmpty()) {
              sampleResponse.setIndicatorNames(
                  indicatorMasters.stream()
                      .map(
                          el ->
                              SampleResponse.IndicatorResponse.builder()
                                  .indicatorId(el.getId())
                                  .indicatorName(el.getIndicatorName())
                                  .build())
                      .toList());
            }
          }
        }
      }
    }
  }

  private void setAmbBlankDetailsOfCaseSample(Cases caseData, SampleResponse sampleResponse) {
    PageRequest pageRequest =
        PageRequest.of(0, 1, Sort.by(DESC, AbstractAuditingEntity_.MODIFIED_DATE));
    List<PlateRunData> samplePlateRunDataBySampleIdAndCaseId =
        samplePlateRunRepository.findSamplePlateRunDataBySampleIdAndCaseId(
            sampleResponse.getSampleId(), caseData.getCaseId(), pageRequest);
    sampleResponse.setAmbBlank(
        ambBlankValueForDifferentRuns(samplePlateRunDataBySampleIdAndCaseId));
  }

  public String ambBlankValueForDifferentRuns(List<PlateRunData> plateRunData) {
    String ambBlank = "";
    if (!plateRunData.isEmpty()) {
      ambBlank = getAmbBlankDataForLatestPlateRun(AMB_BLANK_CONTROL_ID, plateRunData.get(0));
    }
    return ambBlank;
  }

  @Override
  public Cases sendToPHDSupervisorReview(Cases aCase, UserMaster user) {
    aCase.setCaseProgressStatus(UN_ASSIGNED);
    aCase.setCaseAssignee(null);
    aCase.setModifiedUser(user);
    aCase.setCaseStatus(CaseStatus.PHD_SUPERVISOR_REVIEW);
    saveToCaseReviewLog(aCase, PRIMARY_SUPERVISOR_REVIEW, user);
    saveCaseHistory(aCase.getCaseId(), user, CaseStatus.PHD_SUPERVISOR_REVIEW.getStatus());
    List<Report> reports = reportService.getReportsByParentIsNull(aCase);
    reports.forEach(report -> changeCaseApprovalStatusToFalse(aCase, report));
    Cases cases = caseRepo.save(aCase);
    piSupport.setCaseNumber(cases.getCaseNumber());
    piSupport.setCaseUpdate(true);
    return cases;
  }

  @Override
  public List<CaseReportExportResponse> getActiveLociOfCaseToExport(Long caseId) {
    Cases aCase = getCase(caseId);
    List<ReportMarker> activeReportMarkers =
        reportMarkerRepository.getReportMarkersByReportIdOrderBySample(
            aCase.getGroupId(), Boolean.TRUE);
    List<CaseReportExportResponse> caseReportExportResponses = new LinkedList<>();
    getDistinctReportMarker(activeReportMarkers)
        .forEach(
            rm -> {
              CaseReportExportResponse exportResponse = new CaseReportExportResponse();
              exportResponse.setMarker(rm.getMarker().getMarkerName());
              exportResponse.setMarkerStatus(rm.getMarkerStatus());
              exportResponse.setSampleInfo(
                  rm.getReportSample().getSample().getExportSampleNumber());
              exportResponse.setEmptySpaceOne("");
              exportResponse.setEmptySpaceTwo("");
              exportResponse.setAllele1(rm.getAllele1());
              exportResponse.setAllele2(rm.getAllele2());
              caseReportExportResponses.add(exportResponse);
            });
    return caseReportExportResponses;
  }

  private List<ReportMarker> getDistinctReportMarker(List<ReportMarker> reportMarkers) {
    Set<String> keySet = new HashSet<>();
    List<ReportMarker> distinctReportMarkers = new ArrayList<>();
    for (var rm : reportMarkers) {
      String key =
          rm.getReportSample().getSample().getSampleCode() + rm.getMarker().getMarkerName();
      if (!keySet.contains(key)) {
        keySet.add(key);
        distinctReportMarkers.add(rm);
      }
    }
    return distinctReportMarkers;
  }

  private String getAmbBlankDataForLatestPlateRun(Long controlId, PlateRunData plateRun) {
    String ambBlankData = "";
    List<PlateQCProcess> ambBlankDetailsByRunIdAndControlId =
        plateService.findAmbBlankDetailsByRunIdAndControlId(plateRun.getRunId(), controlId);
    List<String> plateControls =
        plateRun.getPlateRunControlMappings().stream()
            .map(PlateRunControlMapping::getControlMaster)
            .collect(Collectors.toList())
            .stream()
            .map(ControlMaster::getControlName)
            .collect(Collectors.toList());
    if (!ambBlankDetailsByRunIdAndControlId.isEmpty()) {
      ambBlankData = PASS;
    } else {
      if (plateControls.contains("Amp-blank")) {
        ambBlankData = FAIL;
      }
    }
    return ambBlankData;
  }

  /**
   * add reviewer details
   *
   * @param caseData
   * @param caseResponse
   */
  private void addReviewDetails(Cases caseData, CaseResponse caseResponse) {
    Optional<CaseReviewLog> caseReviewLog = getCaseReviewLog(caseData, CASE_REVIEW_TYPE_DATA);
    if (caseReviewLog.isPresent() && caseResponse != null) {
      CaseReviewLog reviewLog = caseReviewLog.get();
      if (reviewLog.getReviewer() != null) {
        caseResponse.setPrimaryReviewer(reviewLog.getReviewer().getUserId());
      }
    }
    Optional<CaseReviewLog> caseReviewPhdLog = getCaseReviewLog(caseData, CASE_REVIEW_TYPE_PHD);
    if (caseReviewPhdLog.isPresent() && caseResponse != null) {
      CaseReviewLog reviewPdLog = caseReviewPhdLog.get();
      if (reviewPdLog.getReviewer() != null) {
        caseResponse.setSecondaryReviewer(reviewPdLog.getReviewer().getUserId());
      }
    }
  }

  /**
   * add indicators to case response
   *
   * @param caseData
   * @param caseResponse
   */
  public void addIndicatorResponseInCaseDetailsResponse(Cases caseData, CaseResponse caseResponse) {
    List<IndicatorMaster> indicatorMasterList = getIndicatorsOfCaseId(caseData.getCaseId());
    if (!indicatorMasterList.isEmpty() && caseResponse != null) {
      caseResponse.setIndicatorResponses(indicatorResponseConverter.convert(indicatorMasterList));
    }
  }

  /**
   * Save new case with our existing samples, and calculate PI
   *
   * @param caseNumber
   * @param regenerationAction
   */
  @Transactional
  @Override
  public void createAllRDO(Long caseNumber, String regenerationAction) {
    if (StringUtils.isNotBlank(regenerationAction) && regenerationAction.equals(ALL_RDO)) {
      CaseChangeActionRequest reportRegenerationRequest = new CaseChangeActionRequest();
      reportRegenerationRequest.setChangeAction(regenerationAction);
      Cases aCase =
          labVantageIntegrationService
              .buildCase(Collections.singletonList(caseNumber), null)
              .get(0);
      reportRegenerationRequest.setCaseNumber(aCase.getCaseNumber());
      List<CaseSampleMapping> caseSampleMappings =
          caseSampleMappingRepository.findByCaseDataCaseIdAndIsActive(aCase.getCaseId());
      saveCaseChangeRequest(aCase, reportRegenerationRequest);
      CaseTestTypeProjection caseTestTypeProjection = caseRepo.findByCaseId(aCase.getCaseId());
      piCalculationFactory.create(
          caseSampleMappings, caseTestTypeProjection, null, null, null, null);
    } else {
      throw new BadRequestException(errorMessage.getInvalidCaseChangeAction());
    }
  }

  @Override
  public List<CaseChangeActionResponse> getCaseChangeActionDetails(Cases caseData) {
    List<CaseChangeRequest> caseChangeRequests =
        caseChangeRequestRepository.findByCaseData(caseData);
    List<CaseChangeActionResponse> caseChangeActionResponses = new ArrayList<>();
    if (!CollectionUtils.isEmpty(caseChangeRequests)) {
      caseChangeRequests.forEach(
          caseChangeRequest ->
              caseChangeActionResponses.add(
                  CaseChangeActionResponse.builder()
                      .caseChangeAction(caseChangeRequest.getRequestDueToChange())
                      .caseChangeComment(caseChangeRequest.getChangeComment())
                      .logDate(caseChangeRequest.getCreatedDate())
                      .build()));
    }
    return caseChangeActionResponses;
  }

  @Override
  public CaseChangeActionResponse getLatestCaseChangeActionOfCase(Cases caseData) {
    String caseChangeAction = "";
    String caseChangeComment = "";
    PageRequest pageRequest =
        PageRequest.of(0, 1, Sort.by(DESC, AbstractAuditingEntity_.CREATED_DATE));
    List<CaseChangeRequest> regenerateData =
        caseChangeRequestRepository.findRecentRegenerateDataOFCase(
            caseData.getCaseId(), pageRequest);
    if (!CollectionUtils.isEmpty(regenerateData)) {
      CaseChangeRequest caseChangeRequest = regenerateData.get(0);
      caseChangeAction = caseChangeRequest.getRequestDueToChange();
      caseChangeComment = caseChangeRequest.getChangeComment();
    }
    return CaseChangeActionResponse.builder()
        .caseChangeAction(caseChangeAction)
        .caseChangeComment(caseChangeComment)
        .build();
  }

  @Override
  public Boolean isAmendedCase(Cases aCase) {
    return caseChangeRequestRepository.existsByCaseDataAndRequestDueToChangeIn(
        aCase, List.of(CASE_CHANGE_AMEND, CASE_CHANGE_AMEND_WITH_INTERPRETATION_ERROR));
  }

  @Override
  public Boolean isCaseDefinitionUpdatedCase(Cases aCase) {
    return caseChangeRequestRepository.existsByCaseDataAndRequestDueToChangeIn(
        aCase, List.of(CASE_CHANGE_CASE_DEFINITION, CASE_DEF_UPDATE_WITH_STATUS_CHANGE));
  }

  @Override
  public Boolean isReprintedCase(Cases aCase) {
    return caseChangeRequestRepository.existsByCaseDataAndRequestDueToChangeIn(
        aCase,
        List.of(
            CASE_CHANGE_REPRINT,
            CASE_CHANGE_REPRINT_WITH_INTERPRETATION_ERROR,
            CASE_CHANGE_REPRINT_WITH_FETUS_GENDER_CHANGE));
  }

  public List<IndicatorMaster> getIndicatorsOfCaseId(Long caseId) {
    return caseIndicatorMappingRepository.findDistinctIndicatorsOfCase(caseId);
  }

  /*
   * Creating roles list using each test type
   *
   * @param samples
   * @param title
   */
  @Override
  public void removingUnMatchingSamples(List<Samples> samples, String title) {
    title = title.trim();
    if (title.equalsIgnoreCase(MATERNITY_MOTHERLESS)) {
      List<String> roles = Arrays.asList(CHILD, ALLEGED_MOTHER, MOTHER, FETUS);
      removingUnMatchingSamples(samples, roles);
    } else if (title.equalsIgnoreCase(MOTHERLESS)) {
      List<String> roles = Arrays.asList(CHILD, ALLEGED_FATHER, FETUS);
      removingUnMatchingSamples(samples, roles);
    } else if (title.equalsIgnoreCase(MATERNITY_TRIO)) {
      List<String> roles =
          Arrays.asList(CHILD, PIConstants.ALLEGED_FATHER, MOTHER, ALLEGED_MOTHER, FETUS);
      removingUnMatchingSamples(samples, roles);
    } else if (title.equalsIgnoreCase(MATERNITY)) {
      List<String> roles = Arrays.asList(CHILD, MOTHER, ALLEGED_MOTHER, FETUS);
      removingUnMatchingSamples(samples, roles);
    }
  }

  /**
   * Removing sample from sample list using roles of each test type
   *
   * @param samples
   * @param roles
   */
  private void removingUnMatchingSamples(List<Samples> samples, List<String> roles) {
    List<String> sampleRoles =
        samples.stream()
            .map(sample -> sample.getTestPartyRole().getRole())
            .collect(Collectors.toList());
    sampleRoles.removeAll(roles);
    for (String role : sampleRoles) {
      samples.removeIf(sample -> sample.getTestPartyRole().getRole().equals(role));
    }
  }

  /**
   * Build CaseDetailResponse using these inputs
   *
   * @param caseId
   * @param caseDetailResponse
   * @param testTypeSubCategory
   * @param caseResponse
   * @param samples
   * @param report
   * @return CaseDetailResponse
   */
  @Override
  public CaseDetailResponse buildCaseDetailResponse(
      Long caseId,
      CaseDetailResponse caseDetailResponse,
      TestTypeSubCategory testTypeSubCategory,
      CaseResponse caseResponse,
      List<Samples> samples,
      Report report) {
    Cases caseData = getCase(caseId);
    AdditionalCalc additionalCalc =
        additionalCalculationService.getAdditionalCalc(caseData, report);
    List<ReportMarker> activeReportMarkers = new ArrayList<>();
    List<ReportMarker> inActiveReportMarkers = new ArrayList<>();
    if (report.getParent() == null) {
      activeReportMarkers =
          reportMarkerRepository.getReportMarkersByReportId(report.getId(), Boolean.TRUE);
      inActiveReportMarkers =
          reportMarkerRepository.getReportMarkersByReportId(report.getId(), Boolean.FALSE);
    } else {
      activeReportMarkers =
          reportMarkerRepository.getAdditionalReportMarkersByReportId(report.getId(), Boolean.TRUE);
      inActiveReportMarkers =
          reportMarkerRepository.getAdditionalReportMarkersByReportId(
              report.getId(), Boolean.FALSE);
    }

    List<NippCaseDetails> nippCaseDetailsList =
        nippCaseDetailsRepository.findByCaseData(report.getCaseId());
    List<NippCaseDataResponse> nippCaseDataResponseList =
        nippCaseDetailsList.stream()
            .map(
                nippCaseDetails -> {
                  String probability = getNippProbability(nippCaseDetails);

                  ReportResultMaster result = nippCaseResult(nippCaseDetails.getInterpretation());

                  return NippCaseDataResponse.builder()
                      .cpi(nippCaseDetails.getCpi())
                      .probability(probability)
                      .snps(nippCaseDetails.getSnps())
                      .fetusGender(nippFetusGender(nippCaseDetails.getFetusGender()))
                      .overriddenFetusGender(
                          nippCaseDetails.getOverriddenFetusGender() != null
                              ? nippFetusGender(nippCaseDetails.getOverriddenFetusGender())
                              : null)
                      .fetusGenderRequired(
                          Boolean.TRUE.equals(
                              nippCaseDetails.getCaseData().getDisplayFetusGender()))
                      .result(result.getRptResultType())
                      .resultId(result.getId())
                      .afConcordance(nippCaseDetails.getAfConcordance())
                      .afConcordanceFlag(nippCaseDetails.getAfConcordanceFlag())
                      .motherConcordance(nippCaseDetails.getMotherConcordance())
                      .motherConcordanceFlag(nippCaseDetails.getMotherConcordanceFlag())
                      .motherQCMetricsRollup(nippCaseDetails.getMotherQCMetricsRollup())
                      .mother2qcRollup(nippCaseDetails.getMother2qcRollup())
                      .father2qcRollup(nippCaseDetails.getFather2qcRollup())
                      .qcSampleMetricsRollup(nippCaseDetails.getQcSampleMetricsRollup())
                      .plateId(
                          nippCaseDetails.getPlate() != null
                              ? nippCaseDetails.getPlate().getPlateId()
                              : null)
                      .plateNumber(
                          nippCaseDetails.getPlate() != null
                              ? nippCaseDetails.getPlate().getAnalysisPlateNumber()
                              : null)
                      .primary(Boolean.TRUE.equals(nippCaseDetails.getStatus()))
                      .build();
                })
            .collect(Collectors.toList());
    caseDetailResponse.setNippCaseDetails(nippCaseDataResponseList);
    setApprovalButton(caseDetailResponse, testTypeSubCategory, report);

    caseDetailResponse.setSnpCaseDetails(getSnpCaseDetails(caseData));

    getValuesFromTestTypeSettingsByTestType(caseDetailResponse, testTypeSubCategory, report);
    String reportTestTypeSubCategory = null;
    if (report.getTestTypeSubCategory() != null) {
      reportTestTypeSubCategory = report.getTestTypeSubCategory().getTestTypeSubCategory();
    }
    activeReportMarkers = filterYStrMarkers(report, activeReportMarkers, reportTestTypeSubCategory);
    inActiveReportMarkers =
        filterYStrMarkers(report, inActiveReportMarkers, reportTestTypeSubCategory);
    List<LociResponse> activeLoci =
        buildLociResponses(activeReportMarkers, samples, true, reportTestTypeSubCategory);
    List<LociResponse> unUsedLoci =
        buildLociResponses(inActiveReportMarkers, samples, false, reportTestTypeSubCategory);
    sortMarkerResponse(activeLoci);
    sortMarkerResponse(unUsedLoci);

    List<TranslationProjection> interpretationTranslations =
        reportTranslationRepository.findInterpretationTranslations(
            String.valueOf(testTypeSubCategory.getId()));

    TestTypeSetting testTypeSettingForReportTitle =
        getTestTypeSetting(
            REPORT_TITLE,
            report.getTestTypeSubCategory(),
            report.getCaseId().getTestTypeSubCategory());
    if (testTypeSettingForReportTitle != null
        && caseResponse.getSamples().stream()
            .anyMatch(el -> el.getCaseRole().equalsIgnoreCase(FETUS))) {
      caseDetailResponse.setReportTypeId(
          Integer.valueOf(testTypeSettingForReportTitle.getMetaKey()));
      caseDetailResponse.setTitle(testTypeSettingForReportTitle.getMetaValue());
    }
    caseDetailResponse.setTranslationProjections(interpretationTranslations);

    caseDetailResponse.setCaseResponse(caseResponse);
    caseDetailResponse.setPicalculationLabel(getCalculationLabel(testTypeSubCategory, report));
    caseDetailResponse.setActiveLoci(activeLoci);
    caseDetailResponse.setUnUsedLoci(unUsedLoci);
    return caseDetailResponse;
  }

  @Override
  public List<SnpCaseDetail> getSnpCaseDetails(Cases caseData) {
    List<SnpSampleDetails> snpRecords =
        snpSampleDetailsRepository.findByPlateAndStatusTrue(caseData.getPlate());
    return snpRecords.stream()
        .filter(
            snpSampleDetails ->
                Optional.ofNullable(snpSampleDetails.getSample()).isPresent()
                    && StringUtils.equals(
                        caseData.getCaseNumber(), snpSampleDetails.getCaseData().getCaseNumber()))
        .map(
            snpRecord -> {
              String snpResult =
                  StringUtils.equals(snpRecord.getSnpValue1(), snpRecord.getSnpValue2())
                      ? AppConstants.HOMOZYGOUS
                      : AppConstants.HETEROZYGOUS;
              return new SnpCaseDetail(
                      caseData.getCaseNumber(),
                      snpRecord.getSnp(),
                      rsMasterDataRepository.findGeneVariantByRsId(snpRecord.getSnp()).orElse("-"),
                      StringUtils.capitalize(snpResult),
                      fetchSODateForGivenCase(caseData),
                      snpRecord.getSnpPosition(),
                      snpRecord.getSnpValue1(),
                      snpRecord.getSnpValue2());
            })
        .toList();

  }

  private String fetchSODateForGivenCase(Cases caseData) {
    List<Report> report = reportRepository.findByCaseIdAndIsActiveTrue(caseData);
    Instant soDate = report.stream()
                            .filter(r -> r.getTestTypeSubCategory() != null &&
                                    METHYL_DETOX.equals(r.getTestTypeSubCategory().getTestTypeSubCategory()))
                            .map(r -> Optional.ofNullable(r.getSoDate()))
                            .filter(Optional::isPresent)
                            .map(Optional::get)
                            .findAny()
                            .orElseGet(Instant::now);
    return DateUtils.adjustUTCDateToEST(soDate);
  }

  @Override
  public String getNippProbability(NippCaseDetails nippCaseDetails) {
    double probability = 0.0;
    double cpi;
    try {
      cpi = Double.parseDouble(nippCaseDetails.getCpi());
    } catch (Exception e) {
      log.error("Probability parse error");
      return "NA";
    }
    if (Objects.equals(nippCaseResult(nippCaseDetails.getInterpretation()).getId(), INCLUSION_ID)) {
      if (StringUtils.isNotBlank(nippCaseDetails.getCpi())) {
        probability = (Math.pow(10, cpi) / (Math.pow(10, cpi) + 1)) * 100;
      }
    } else {
      return "0";
    }
    if (probability > 99.9) {
      return ">99.9";
    }
    return String.format("%.1f", probability);
  }

  @Override
  public ReportResultMaster nippCaseResult(String resultText) {
    if (resultText == null) resultText = "";
    switch (resultText) {
      case "COMPATIBLE" -> {
        return reportResultMasterRepository.findById(INCLUSION_ID).orElse(null);
      }
      case "NOT_COMPATIBLE" -> {
        return reportResultMasterRepository.findById(EXCLUSION_ID).orElse(null);
      }
      default -> {
        return reportResultMasterRepository.findById(INCONCLUSIVE_ID).orElse(null);
      }
    }
  }

  @Override
  public String nippFetusGender(String genderText) {
    switch (genderText) {
      case "Y_NOT_PRESENT" -> {
        return "Female";
      }
      case "Y_PRESENT" -> {
        return "Male";
      }
      case "Male" -> {
        return "Y_PRESENT";
      }
      case "Female" -> {
        return "Y_NOT_PRESENT";
      }
      default -> {
        return UNKNOWN_GENDER;
      }
    }
  }

  /**
   * @param testTypeSubCategory verifying prenatal test types
   * @param activeLoci loci list to filter
   * @return filtered loci list with amel markers removed from active loci
   */
  @Override
  public List<LociResponse> removeAmelIfTestTypeIsPrenatel(
      String testTypeSubCategory, List<LociResponse> activeLoci) {
    if (PIConstants.PRENATAL_TEST_TYPES.contains(testTypeSubCategory)) {
      activeLoci.removeIf(
          activeLociValue -> Objects.equals(activeLociValue.getLocus(), AMELOGENIN));
    }
    return activeLoci;
  }

  private void sortMarkerResponse(List<LociResponse> lociList) {
    lociList.sort(
        Comparator.comparing(
            LociResponse::getSortOrder, Comparator.nullsLast(Comparator.naturalOrder())));
  }

  private void setApprovalButton(
      CaseDetailResponse caseDetailResponse,
      TestTypeSubCategory testTypeSubCategory,
      Report report) {
    TestTypeSubCategory reportTestTypeSubCategory = report.getTestTypeSubCategory();
    Optional<TestTypeSetting> optionalPICalcBlockButton =
        testTypeSettingRepository.findByTestTypeSubCategoryAndSettingForAndMetaKeyAndReportTestType(
            testTypeSubCategory,
            APPROVAL_BUTTONS,
            BLOCK_COMBINED_INDEX_APPROVE_BUTTON,
            reportTestTypeSubCategory);

    Optional<TestTypeSetting> optionalReportBlockButton =
        testTypeSettingRepository.findByTestTypeSubCategoryAndSettingForAndMetaKeyAndReportTestType(
            testTypeSubCategory,
            APPROVAL_BUTTONS,
            BLOCK_REPORTINFO_APPROVE_BUTTON,
            reportTestTypeSubCategory);

    Optional<TestTypeSetting> optionalResultBlockButton =
        testTypeSettingRepository.findByTestTypeSubCategoryAndSettingForAndMetaKeyAndReportTestType(
            testTypeSubCategory,
            APPROVAL_BUTTONS,
            BLOCK_ACTIVELOCI_APPROVE_BUTTON,
            reportTestTypeSubCategory);

    Optional<TestTypeSetting> optionalInterpretationBlockButton =
        testTypeSettingRepository.findByTestTypeSubCategoryAndSettingForAndMetaKeyAndReportTestType(
            testTypeSubCategory,
            APPROVAL_BUTTONS,
            BLOCK_INTERPRETATION_APPROVE_BUTTON,
            reportTestTypeSubCategory);

    Optional<TestTypeSetting> optionalExportRequired =
        testTypeSettingRepository.findByTestTypeSubCategoryAndSettingForAndMetaKey(
            testTypeSubCategory, EXPORT, EXPORT_REQUIRED);

    Optional<TestTypeSetting> optionalCombinedCPI =
        testTypeSettingRepository.findByTestTypeSubCategoryAndSettingForAndMetaKeyAndReportTestType(
            testTypeSubCategory, DISPLAY_SECTIONS, BLOCK_COMBINED_CPI, reportTestTypeSubCategory);
    optionalExportRequired.ifPresent(
        testTypeSetting ->
            caseDetailResponse.setExportRequired(
                Boolean.parseBoolean(testTypeSetting.getMetaValue())));
    caseDetailResponse.setBlockInterpretationApproveButton(
        optionalInterpretationBlockButton.isPresent());
    caseDetailResponse.setBlockCombinedIndexApproveButton(optionalPICalcBlockButton.isPresent());
    caseDetailResponse.setBlockReportInfoApproveButton(optionalReportBlockButton.isPresent());
    caseDetailResponse.setBlockActiveLociApproveButton(optionalResultBlockButton.isPresent());
    caseDetailResponse.setBlockCombinedCPIArea(optionalCombinedCPI.isEmpty());
  }

  private void getValuesFromTestTypeSettingsByTestType(
      CaseDetailResponse caseDetailResponse,
      TestTypeSubCategory testTypeSubCategory,
      Report report) {
    Optional<TestTypeSetting> optionalTestTypeFirstDegree =
        testTypeSettingRepository.findByTestTypeSubCategoryAndSettingForAndMetaKey(
            testTypeSubCategory, META_KEY_AIPI_LABEL, FIRST_DEGREE);

    Optional<TestTypeSetting> optionalTestTypeSecondDegree =
        testTypeSettingRepository.findByTestTypeSubCategoryAndSettingForAndMetaKey(
            testTypeSubCategory, META_KEY_AIPI_LABEL, SECOND_DEGREE);

    Optional<TestTypeSetting> optionalTestTypeAIPILabel =
        testTypeSettingRepository.findByTestTypeSubCategoryAndSettingForAndMetaKey(
            testTypeSubCategory, META_KEY_AIPI_LABEL, AIPIlabel);

    Optional<TestTypeSetting> optionalTestTypeAIPITwoLabel =
        testTypeSettingRepository.findByTestTypeSubCategoryAndSettingForAndMetaKey(
            testTypeSubCategory, META_KEY_AIPI_LABEL, AIPITwoLabel);

    TestTypeSettingResponse testTypeSettings =
        testTypeService.getTestTypeSettings(testTypeSubCategory);
    caseDetailResponse.setBlockCombinedIndexArea(testTypeSettings.isBlockCombinedIndexArea());
    caseDetailResponse.setBlockPIAndCalculationArea(testTypeSettings.isBlockPIAndCalculationArea());
    caseDetailResponse.setBlockInterpretationArea(testTypeSettings.isBlockInterpretationArea());
    caseDetailResponse.setDivideGpiValue(
        caseDetailResponse.getCpi() != null && caseDetailResponse.getCpi() < 1);

    optionalTestTypeFirstDegree.ifPresent(
        testTypeSetting -> caseDetailResponse.setFirstDegree(testTypeSetting.getMetaValue()));
    optionalTestTypeSecondDegree.ifPresent(
        testTypeSetting -> caseDetailResponse.setSecondDegree(testTypeSetting.getMetaValue()));
    optionalTestTypeAIPILabel.ifPresent(
        testTypeSetting -> caseDetailResponse.setAipiLabel(testTypeSetting.getMetaValue()));
    optionalTestTypeAIPITwoLabel.ifPresent(
        testTypeSetting -> caseDetailResponse.setAipiTwoLabel(testTypeSetting.getMetaValue()));

    Optional<TestTypeSetting> testTypeSettingManualMutation =
        testTypeSettingRepository.findByTestTypeSubCategoryAndSettingForAndMetaKey(
            report.getTestTypeSubCategory(), MANUAL_MUTATION, MANUAL_MUTATION_META_KEY);
    caseDetailResponse.setBlockManualMutation(testTypeSettingManualMutation.isEmpty());
  }

  @Override
  public CaseDetailResponse getCaseDetailsOfReport(Report report) {
    CaseDetailResponse caseDetailResponse = new CaseDetailResponse();
    CaseResponse caseResponse = caseToCaseResponseConverter.convert(report.getCaseId());
    assert caseResponse != null;
    List<CaseSampleMapping> caseSampleMappings = getSamplesFromCase(report.getCaseId().getCaseId());
    List<Samples> samples = caseSampleMappings.stream().map(CaseSampleMapping::getSamples).toList();
    List<SampleResponse> sampleResponses =
        sampleToSampleResponseConverter.convert(
            samples, report.getCaseId(), caseSampleMappings, report);
    caseResponse.setSamples(sampleResponses);
    addReportInformationToCaseDetailResponse(report.getCaseId(), caseDetailResponse, report, false);
    return buildCaseDetailResponse(
        report.getCaseId().getCaseId(),
        caseDetailResponse,
        report.getCaseId().getTestTypeSubCategory(),
        caseResponse,
        samples,
        report);
  }

  @Override
  public List<CaseSampleMapping> getSamplesFromCase(Long caseId) {
    return caseSampleMappingRepository.findByCaseDataCaseId(caseId).stream()
        .peek(cs -> cs.getSamples().setTestPartyRole(cs.getTestPartyRole()))
        .toList();
  }

  public List<Samples> getActiveSamplesFromCase(Long caseId) {
    List<CaseSampleMapping> caseSampleMappings =
        caseSampleMappingRepository.findByCaseDataCaseIdAndIsActive(caseId);
    return caseSampleMappings.stream().map(CaseSampleMapping::getSamples).toList();
  }

  public List<CaseSampleMapping> getSampleMappingsFromCase(Long caseId) {
    return caseSampleMappingRepository.findByCaseDataCaseId(caseId);
  }

  @Override
  public CaseDetailResponse addReportInformationToCaseDetailResponse(
      Cases caseData, CaseDetailResponse caseDetailResponse, Report report, boolean flag) {
    if (report.getTestTypeSubCategory() != null)
      caseDetailResponse.setReportTypeId(report.getTestTypeSubCategory().getId());
    caseDetailResponse.setNoOfMutations(report.getNoMut());
    caseDetailResponse.setCai(report.getCai());
    caseDetailResponse.setCaiSecondDegree(report.getCaiSecondDegree());
    caseDetailResponse.setAiPI(report.getAiPI());
    caseDetailResponse.setAiPISecondDegree(report.getAiPISecondDegree());
    ReportResultMaster reportResult = report.getReportResult();
    if (reportResult != null)
      caseDetailResponse.setReportResultResponse(
          reportResultToReportResultResponseConverter.convert(reportResult));
    caseDetailResponse.setReportId(report.getId());
    caseDetailResponse.setIsYstrTestPanelFound(Boolean.TRUE.equals(report.getYstrTestPanelFound()));
    caseDetailResponse.setIsXstrTestPanelFound(Boolean.TRUE.equals(report.getXstrTestPanelFound()));
    TestPartyRace testPartyRace = report.getTestPartyRace();
    caseDetailResponse.setRace(testPartyRace != null ? testPartyRace.getRace() : null);
    setCalculationReport(caseDetailResponse, report);
    return caseDetailResponse;
  }

  /**
   * @param caseDetailResponse for setting values
   * @param report to fetch test type sub category. if the all pi values inside the report marker is
   *     empty cpi/residual-cpi/pop will be null in response.
   */
  private void setCalculationReport(CaseDetailResponse caseDetailResponse, Report report) {
    boolean testTypeAndRprCheck = reportService.checkTestTypeForReconAndDna(report);
    String testTypeCategory = report.getTestTypeSubCategory().getTestTypeSubCategory();
    if (cpiService
            .getReportMarkerInfoBasedOnAdditionalCalculationOrNot(report.getId(), null)
            .stream()
            .map(ReportMarkerInfo::getPi)
            .allMatch(Objects::isNull)
        && !testTypeCategory.equals(DNA_COMPARISON)
        && !testTypeCategory.equals(Y_STR_ALLELE_SIZING_WITHOUT_RECONSTRUCT)) {
      caseDetailResponse.setCpi(null);
      caseDetailResponse.setResidualCPi(null);
      caseDetailResponse.setProbability(null);
    } else {
      DecimalFormat df = DECIMAL_FORMAT_TWENTY;
      DecimalFormat probDf = DECIMAL_FORMAT_TWENTY;
      caseDetailResponse.setResidualCPi(
          report.getRawCPI() != null && testTypeAndRprCheck
              ? Double.valueOf(
                  reportService.formatByEncounterOfFirstNonZeroDigit(
                      String.format("%.20f", report.getRawCPI())))
              : formatResidualCpiValue(report));
      caseDetailResponse.setCpi(
          testTypeAndRprCheck
              ? Double.valueOf(
                  reportService.formatByEncounterOfFirstNonZeroDigit(
                      String.format("%.20f", report.getCpi())))
              : formatCpiValue(report));
      caseDetailResponse.setProbability(
          testTypeAndRprCheck
              ? reportService.formatByEncounterOfFirstNonZeroDigit(
                  probDf.format(Double.valueOf(reportService.getProbabilityPercentage(report))))
              : reportService.getProbabilityPercentage(report));
      caseDetailResponse.setRawCpi(
          caseDetailResponse.getCpi() != null ? caseDetailResponse.getCpi() : null);
      caseDetailResponse.setRawProbability(
          caseDetailResponse.getProbability() != null ? caseDetailResponse.getProbability() : null);
      if (report
          .getTestTypeSubCategory()
          .getTestTypeSubCategory()
          .equals(Y_STR_ALLELE_SIZING_WITHOUT_RECONSTRUCT)) {
        caseDetailResponse.setNoOfMutations(report.getNoMut());
      }
    }
  }

  @Override
  public Double formatCpiValue(Report report) {
    if (report.getCpi() == null) return null;
    Double roundedCpi;
    try {
      String cpiValue = cpiService.roundCpiValue(report.getCpi()).replace(",", "");
      roundedCpi = Double.parseDouble(cpiValue);
    } catch (NullPointerException | NumberFormatException ex) {
      log.error(ex.getMessage());
      roundedCpi = report.getCpi() != null ? report.getCpi() : null;
    }
    return roundedCpi;
  }

  @Override
  public Double formatCpi(Double cpi) {
    if (cpi == null) return null;
    Double roundedCpi;
    try {
      String cpiValue = cpiService.roundCpiValue(cpi).replace(",", "");
      roundedCpi = Double.parseDouble(cpiValue);
    } catch (NullPointerException | NumberFormatException ex) {
      log.error(ex.getMessage());
      roundedCpi = cpi != null ? cpi : null;
    }
    return roundedCpi;
  }

  @Override
  public Double formatResidualCpiValue(Report report) {
    if (report.getCpi() == null) return null;
    Double roundedCpi;
    try {
      String cpiValue = cpiService.roundCpiValue(report.getRawCPI()).replace(",", "");
      roundedCpi = Double.parseDouble(cpiValue);
    } catch (NullPointerException | NumberFormatException ex) {
      log.error(ex.getMessage());
      roundedCpi = report.getRawCPI() != null ? report.getRawCPI() : null;
    }
    return roundedCpi;
  }

  @Override
  public List<LociResponse> buildLociResponses(
      List<ReportMarker> reportMarkers,
      List<Samples> samples,
      boolean isUsed,
      String reportTestTypeSubCategory) {
    List<LociResponse> lociResponses = new ArrayList<>();
    Map<MarkerMaster, List<ReportMarker>> markerReportMarkerMap =
        reportMarkers.stream()
            .collect(Collectors.groupingBy(ReportMarker::getMarker, Collectors.toList()));
    Optional<TestTypeSetting> xstrMarkerStripColorOptional =
        testTypeSettingRepository.findBySettingForAndMetaKey(
            "markerStripColor", X_STR_BATTERY_ID.toString());
    Optional<TestTypeSetting> ystrMarkerStripColorOptional =
        testTypeSettingRepository.findBySettingForAndMetaKey(
            "markerStripColor", Y_STR_BATTERY_ID.toString());
    for (MarkerMaster markerMaster : markerReportMarkerMap.keySet()) {
      List<ReportMarker> reportMarkersList = markerReportMarkerMap.get(markerMaster);
      Optional<ReportMarker> standardReportMarkerOptional = reportMarkersList.stream().findFirst();
      if (standardReportMarkerOptional.isPresent()) {
        ReportMarker rm = standardReportMarkerOptional.get();
        LociResponse lociResponse = new LociResponse();
        lociResponse.setLocus(rm.getMarker().getMarkerName());
        lociResponse.setIsYstrMarker(Boolean.TRUE.equals(rm.getIsYstr()));
        lociResponse.setSortOrder(rm.getMarker().getSortOrder());
        if (reportService.isMarkerWithSpecificTestPanel(
                X_STR_BATTERY_ID, markerMaster.getMarkerName())
            && xstrMarkerStripColorOptional.isPresent()) {
          lociResponse.setMarkerColorStrip(xstrMarkerStripColorOptional.get().getMetaValue());
        } else if (reportService.isMarkerWithSpecificTestPanel(
                Y_STR_BATTERY_ID, markerMaster.getMarkerName())
            && ystrMarkerStripColorOptional.isPresent()) {
          lociResponse.setMarkerColorStrip(ystrMarkerStripColorOptional.get().getMetaValue());
        }
        DecimalFormat df = new DecimalFormat("#.####################");
        lociResponse.setPi(
            rm.getPi() != null
                ? reportService.formatByEncounterOfFirstNonZeroDigit(df.format(rm.getPi()))
                : null);
        lociResponse.setIsManualMutation(rm.getManualMutation());
        lociResponse.setCalculation(rm.getFormula());
        lociResponse.setAiFirstDegree(rm.getAiFirstDegree());
        lociResponse.setAiSecondDegree(rm.getAiSecondDegree());
        lociResponse.setPiDisplayStyle(rm.getPIDisplayStyle());
        List<LociResponse.AlleleValues> alleleValues =
            reportMarkersList.stream()
                .map(
                    reportMarkerBySample -> {
                      LociResponse.AlleleValues alleleValue = new LociResponse.AlleleValues();
                      alleleValue.setAllele1(reportMarkerBySample.getAllele1());
                      alleleValue.setAllele2(reportMarkerBySample.getAllele2());
                      alleleValue.setReportMarkerId(reportMarkerBySample.getId());
                      alleleValue.setStatus(reportMarkerBySample.getMarkerStatus());
                      alleleValue.setSampleId(
                          reportMarkerBySample.getReportSample().getSample().getSampleId());
                      alleleValue.setSampleCode(
                          reportMarkerBySample.getReportSample().getSample().getSampleCode());
                      return alleleValue;
                    })
                .collect(Collectors.toList());
        lociResponse.setAlleleValues(alleleValues);
        lociResponses.add(lociResponse);
      }
    }
    return lociResponses;
  }

  @Override
  public List<ReportMarker> filterYStrMarkers(
      Report report, List<ReportMarker> reportMarkers, String reportTestTypeSubCategory) {
    // While in the unused marker check that marker associate with YSTR, if associate with YSTR
    // please ignore that in other test types
    return reportMarkers.stream()
        .filter(
            reportMarker -> {
              Optional<TestTypeSubCategoryMarkerMapping> optionalTestTypeSubCategoryMarkerMapping =
                  testTypeSubCategoryMarkerMappingRepository.findByMarkerMasterMarkerName(
                      reportMarker.getMarker().getMarkerName());
              if (reportTestTypeSubCategory != null
                      && reportTestTypeSubCategory.equals(Y_STR_WITH_RECONSTRUCT)
                  || reportTestTypeSubCategory.equals(Y_STR_ALLELE_SIZING_WITHOUT_RECONSTRUCT)
                  || reportTestTypeSubCategory.equals(Y_STR_ALLELE_SIZING)) {
                return optionalTestTypeSubCategoryMarkerMapping.isPresent();
              } else {
                return optionalTestTypeSubCategoryMarkerMapping.isEmpty()
                    || Boolean.TRUE.equals(report.getYstrTestPanelFound());
              }
            })
        .collect(Collectors.toList());
  }

  @Override
  public PICalculationLabelResponse getCalculationLabel(
      TestTypeSubCategory testTypeSubCategory, Report report) {
    PICalculationLabelResponse piCalculationLabelResponse = null;
    if (report != null && report.getTestTypeSubCategory() != null)
      testTypeSubCategory = report.getTestTypeSubCategory();
    Optional<TestTypeSetting> optionalPITestTypeSetting =
        testTypeSettingRepository.findByTestTypeSubCategoryAndSettingForAndMetaKey(
            testTypeSubCategory, PI_CALC_LABEL, META_KEY_PI);
    Optional<TestTypeSetting> optionalCPITestTypeSetting =
        testTypeSettingRepository.findByTestTypeSubCategoryAndSettingForAndMetaKey(
            testTypeSubCategory, PI_CALC_LABEL, META_KEY_CPI);
    Optional<TestTypeSetting> optionalRCPITestTypeSetting =
        testTypeSettingRepository.findByTestTypeSubCategoryAndSettingForAndMetaKey(
            testTypeSubCategory, PI_CALC_LABEL, META_KEY_RCPI);

    Optional<TestTypeSetting> optionalCombinedCPITestTypeSetting =
        testTypeSettingRepository.findByTestTypeSubCategoryAndSettingForAndMetaKey(
            testTypeSubCategory, PI_CALC_LABEL, META_KEY_COMBAINED_CPI);
    if (optionalPITestTypeSetting.isPresent()
        && optionalRCPITestTypeSetting.isPresent()
        && optionalCPITestTypeSetting.isPresent()) {
      piCalculationLabelResponse =
          PICalculationLabelResponse.builder()
              .labelPI(optionalPITestTypeSetting.get().getMetaValue())
              .labelCPI(optionalCPITestTypeSetting.get().getMetaValue())
              .labelResidualCPI(optionalRCPITestTypeSetting.get().getMetaValue())
              .build();
    }
    if ((optionalCombinedCPITestTypeSetting.isPresent()) && piCalculationLabelResponse != null)
      piCalculationLabelResponse.setLabelCombinedCPI(
          optionalCombinedCPITestTypeSetting.get().getMetaValue());
    return piCalculationLabelResponse;
  }

  @Override
  public Optional<Cases> getCaseByCaseNumber(String caseNumber) {
    Cases aCase = caseRepo.findByCaseNumber(caseNumber);
    if (aCase == null) return Optional.empty();
    return Optional.of(aCase);
  }

  /**
   * Get Case
   *
   * @param caseId
   * @return Case
   */
  public Cases getCase(Long caseId) {
    return caseRepo
        .findById(caseId)
        .orElseThrow(() -> new NotFoundException(format(errorMessage.getCaseNotFound(), caseId)));
  }

  /**
   * Change Case progress status
   *
   * @param caseId
   * @param caseProgressStatus
   * @return Case
   */
  @Override
  public Cases changeCaseProgressStatus(
      Long caseId, CaseProgressStatus caseProgressStatus, UserMaster user) {
    if (caseProgressStatus != CaseProgressStatus.IN_PROGRESS) {
      Cases caseData = getCase(caseId);
      caseData.setCaseProgressStatus(caseProgressStatus);
      caseData.setCaseAssignee(null);
      caseData.setModifiedUser(user);
      saveCaseHistory(caseId, user, caseProgressStatus.getStatus());
      return caseRepo.save(caseData);
    } else {
      throw new OperationNotFound(
          format(errorMessage.getOperationNotFound(), caseProgressStatus.name()));
    }
  }

  @Override
  public List<Long> getCaseQCValidationIds(List<QCCaseValidation> qcValidations) {
    return qcValidations.stream().map(QCCaseValidation::getId).collect(Collectors.toList());
  }

  /**
   * get all case level alerts
   *
   * @param caseId
   * @return
   */
  @Override
  public AlertResponse getAllAlertOfCase(Long caseId, Boolean isGroupId) {
    Cases caseData = !isGroupId ? getCase(caseId) : null;
    List<QCCaseValidation> byCaseDataCaseIdAndValidationStatus =
        Boolean.TRUE.equals(isGroupId)
            ? qcCaseValidationRepo.findByCaseDataGroupIdAndValidationStatus(
                caseId, ValidationStatus.ACTIVE)
            : qcCaseValidationRepo.findByCaseDataCaseIdAndValidationStatus(
                caseId, ValidationStatus.ACTIVE);
    if (!CollectionUtils.isEmpty(byCaseDataCaseIdAndValidationStatus)) {
      // get all guarenteed qc
      List<QCCaseValidation> guarenteedQCData =
          byCaseDataCaseIdAndValidationStatus.stream()
              .filter(qcCaseValidation -> qcCaseValidation.getSample() == null)
              .collect(Collectors.toList());
      // removing guarenteed qc from qc list since it does not save sample id while saving
      byCaseDataCaseIdAndValidationStatus.removeAll(guarenteedQCData);
      // creating sample id qc list combination
      Map<Samples, List<QCCaseValidation>> sampleValidationCombo =
          byCaseDataCaseIdAndValidationStatus.stream()
              .collect(Collectors.groupingBy(QCCaseValidation::getSample));
      return caseAlertResponseConverter.convert(sampleValidationCombo, guarenteedQCData);
    }
    return new AlertResponse();
  }

  /**
   * to acknowledge alert
   *
   * @param alertId
   * @param user
   */
  @Override
  public Cases addAcknowledgementOfAlert(Long alertId, UserMaster user) {
    QCCaseValidationAlert qcCaseValidationAlert =
        qcCaseValidationAlertRepo
            .findById(alertId)
            .orElseThrow(
                () -> new NotFoundException(format(errorMessage.getAlertNotFound(), alertId)));
    qcCaseValidationAlert.setAcknowledgmentStatus(Boolean.TRUE);
    qcCaseValidationAlert.setModifiedUser(user);
    qcCaseValidationAlert.setAcknowledgeBy(user);
    qcCaseValidationAlertRepo.save(qcCaseValidationAlert);
    Cases caseData = qcCaseValidationAlert.getQcCaseValidation().getCaseData();
    List<Samples> samples =
        caseData.getCaseSampleMapping().stream()
            .map(CaseSampleMapping::getSamples)
            .collect(Collectors.toList());
    List<Long> sampleIds = samples.stream().map(Samples::getSampleId).collect(Collectors.toList());
    int caseQCAlert =
        qcValidationRepository.getAlertsBySamplesAndAcknowledgeStatus(
            sampleIds, false, ValidationStatus.ACTIVE);
    int plateQcAlertCount =
        qcValidationRepository.getPlateLevelAlertsBySamplesAndAcknowledgeStatusWithoutPlate(
            sampleIds, false, ValidationStatus.ACTIVE);
    Cases aCase = null;
    if (caseQCAlert == 0
        && plateQcAlertCount == 0
        && !piHelper.getActiveSamplesWithoutReprocess(samples, caseData.getCaseId())
        && caseData.getCaseStatus() == CaseStatus.AWAITING_TESTING
        && caseData.getCaseType() != CaseType.CHAIN)
      aCase = changeCaseStatus(caseData, CaseStatus.REVIEW_REQUIRED.name(), user);
    return aCase;
  }

  /**
   * @param caseFilterRequest
   * @param caseFilterData
   * @return CaseSearchRequest
   */
  private CaseSearchRequest buildCaseSearchRequest(
      CaseFilterRequest caseFilterRequest, CaseFilterDataResponse caseFilterData) {
    // CaseStatus Enum string convert to corresponding to integer values
    List<Integer> caseStatusesOrdinal = new ArrayList<>();
    if (caseFilterData.getCaseStatuses() != null) {
      caseStatusesOrdinal =
          caseFilterData.getCaseStatuses().stream()
              .map(CaseStatus::ordinal)
              .collect(Collectors.toList());
    }
    // CaseProgressStatus Enum string convert to corresponding to integer values
    List<Integer> caseProgressStatusOrdinal = new ArrayList<>();
    if (caseFilterData.getCaseProgressStatuses() != null) {
      caseProgressStatusOrdinal =
          caseFilterData.getCaseProgressStatuses().stream()
              .map(CaseProgressStatus::ordinal)
              .toList();
    }
    List<Integer> casePriorityOrdinal = new ArrayList<>();
    if (caseFilterData.getCasePriorities() != null) {
      casePriorityOrdinal =
          caseFilterData.getCasePriorities().stream()
              .map(CasePriority::ordinal)
              .collect(Collectors.toList());
    }
    Date startDate = caseFilterRequest.getFromDate();
    Date endDate = caseFilterRequest.getEndDate();
    // Date convert to starting time of the day
    if (startDate != null) startDate = getStartOfTheDay(startDate);
    // Date convert to last hour of the day
    if (endDate != null) endDate = getEndOfTheDay(endDate);

    Integer caseType = caseFilterRequest.getCaseType();
    Integer testType = caseFilterRequest.getTestType();
    Integer channelId = caseFilterRequest.getChannelId();
    List<TestTypeSubCategory> testTypeSubCategoryList =
        testTypeSubCategoryRepository.findTestTypeSubCategoryByTestTypeId(testType);
    List<Integer> testTypeSubcategoryIds =
        testTypeSubCategoryList.stream()
            .map(TestTypeSubCategory::getId)
            .collect(Collectors.toList());
    if (caseFilterRequest.getIsInHelpFolder() == null) {
      caseFilterRequest.setIsInHelpFolder(false);
    }

    return CaseSearchRequest.builder()
        .userId(caseFilterRequest.getUserId())
        .searchText(caseFilterRequest.getSearchText())
        .caseStatuses(caseStatusesOrdinal)
        .caseProgressStatuses(caseProgressStatusOrdinal)
        .casePriorities(casePriorityOrdinal)
        .start(startDate)
        .end(endDate)
        .caseType(caseType)
        .testType(testTypeSubcategoryIds)
        .channelId(channelId)
        .taggedUserId(caseFilterRequest.getTaggedUserId())
        .redrawFilterMode(caseFilterRequest.getRedrawFilterMode())
        .isInHelpFolder(caseFilterRequest.getIsInHelpFolder())
        .tagFilterMode(caseFilterRequest.getTagFilterMode())
        .modifiedFilterMode(caseFilterRequest.getModifiedFilterMode())
        .entityManager(entityManager)
        .includeNippCases(caseFilterRequest.getShowNippCases())
        .viabilityOnly(caseFilterRequest.getViabilityOnly())
        .isMyCaseTab(caseFilterRequest.getIsMyCaseTab())
        .build();
  }

  /**
   * Change case status
   *
   * @param caseData
   * @param caseStatus
   * @return Case
   */
  @Override
  public Cases changeCaseStatus(Cases caseData, String caseStatus, UserMaster user) {
    if (piSupport.getCaseActiveReportMap().get(caseData.getCaseId()) != null) {
      boolean isAdditionalCalcReport =
          additionalCalculationService.additionalCalculationOrNot(
              piSupport.getCaseActiveReportMap().get(caseData.getCaseId()).getId());
      if (isAdditionalCalcReport) return caseData;
    }
    CaseStatus updatedCaseStatus = CaseStatus.valueOf(caseStatus);
    List<Samples> samples =
        caseSampleMappingRepository.findByCaseDataCaseIdAndIsActive(caseData.getCaseId()).stream()
            .map(CaseSampleMapping::getSamples)
            .collect(Collectors.toList());
    boolean newSampleFound =
        samples.stream()
            .anyMatch(s -> s.getSampleRunStatus() == SampleRunStatus.NEW || s.isRedrawRequested());
    boolean reDrawSampleFound = samples.stream().anyMatch(Samples::isRedrawRequested);
    boolean isUpdatingCase = (piSupport.isChangeCaseAction() || piSupport.isRecalculate());
    if (piSupport.isChangeCaseAction()
        && piSupport.getCaseChangeAction().equalsIgnoreCase(CASE_CHANGE_CASE_DEFINITION)) {
      isUpdatingCase = false;
    }

    List<Samples> reProcessRequestedSamples =
        samples.stream()
            .filter(
                s ->
                    s.getSampleRunStatus() == SampleRunStatus.RE_PROCESS_REQUESTED
                        || s.isRedrawRequested())
            .collect(Collectors.toList());
    Set<Cases> totalCaseList = new HashSet<>();
    for (Samples s : reProcessRequestedSamples) {
      List<Cases> aCaseList =
          s.getCaseSampleMapping().stream()
              .map(CaseSampleMapping::getCaseData)
              .collect(Collectors.toList());
      totalCaseList.addAll(aCaseList);
    }

    if (!isUpdatingCase
        || !newSampleFound
        || reDrawSampleFound
        || updatedCaseStatus == CaseStatus.AWAITING_TESTING) {
      saveCaseHistory(caseData.getCaseId(), user, updatedCaseStatus.getStatus());
      caseData.setCaseStatus(updatedCaseStatus);
      if (reDrawSampleFound) caseData.setOriginalCaseStatus(updatedCaseStatus);
      if (caseData.getCaseStatus() == CaseStatus.REVIEW_REQUIRED
          || caseData.getCaseStatus() == CaseStatus.PHD_REVIEW
          || caseData.getCaseStatus() == CaseStatus.LAB_MANAGER_REVIEW) {
        sampleService.updateSamplesStatus(samples, SampleRunStatus.IN_PROGRESS, user);
      }
    } else if (caseData.getCaseStatus() == CaseStatus.AWAITING_TESTING
        && updatedCaseStatus == CaseStatus.PHD_REVIEW
        && Boolean.TRUE.equals(caseData.getIsNippTestType())) {
      caseData.setCaseStatus(updatedCaseStatus);
    }
    piHelper.setModifiedStatusOfCase(caseData, null);
    for (Cases aCase : totalCaseList) {
      aCase.setCaseStatus(updatedCaseStatus);
      aCase.setOriginalCaseStatus(updatedCaseStatus);
      piSupport.setCaseReportStatusListMap(caseData.getCaseId(), updatedCaseStatus);
    }

    if (user != null) caseData.setModifiedUser(userService.getUser(user.getUserId()));
    if (caseStatus.equals(CaseStatus.LAB_MANAGER_REVIEW.name())) {
      caseData.setCaseProgressStatus(CaseProgressStatus.UN_ASSIGNED);
      caseData.setCaseAssignee(null);
    }
    if (user != null
        && userService.isRoleSuperVisor(user)
        && caseData.getCaseStatus() == CaseStatus.SUPERVISOR_REVIEW)
      caseData.setCaseProgressStatus(UN_ASSIGNED);
    if (caseStatus == CaseStatus.SUPERVISOR_REVIEW.name()) {
      caseData.setCaseStatus(CaseStatus.SUPERVISOR_REVIEW);
      List<CaseIndicatorMapping> sampleRDOIndicators =
          getIndicatorsOfCaseIdAndSlug(caseData.getCaseId(), SAMPLE_RDO);
      sampleRDOIndicators.addAll(
          getIndicatorsOfCaseIdAndSlug(caseData.getCaseId(), SAMPLE_RDO_EXT_LAB));
      if (!sampleRDOIndicators.isEmpty()) {
        saveToCaseReviewLog(caseData, PRIMARY_DATA_REVIEW, user);
      }
    }
    Report report = piSupport.getCaseActiveReportMap().get(caseData.getCaseId());
    if (report == null)
      report = reportService.getReportsByParentIsNull(caseData).stream().findFirst().orElse(null);
    reconstructBasedStatusChange(caseData, report);
    Cases cases = caseRepo.save(caseData);
    piSupport.setCaseNumber(cases.getCaseNumber());
    piSupport.setCaseUpdate(true);
    return cases;
  }

  @Override
  public void setOriginalCaseStatus(Cases aCase) {
    aCase.setOriginalCaseStatus(aCase.getCaseStatus());
    caseRepo.save(aCase);
  }

  @Override
  public void reconstructBasedStatusChange(Cases caseData, Report report) {
    if (report != null && piHelper.isReconstruct(report)) {
      if (Boolean.TRUE.equals(report.getYstrTestPanelFound())
          || Boolean.TRUE.equals(report.getXstrTestPanelFound())) {
        if (caseData.getCaseStatus() == CaseStatus.REVIEW_REQUIRED) {
          caseData.setCaseStatus(CaseStatus.SUPERVISOR_REVIEW);
          caseData.setCaseProgressStatus(UN_ASSIGNED);
        } else if (caseData.getCaseStatus() == CaseStatus.PHD_REVIEW) {
          caseData.setCaseStatus(CaseStatus.PHD_SUPERVISOR_REVIEW);
        }
      }
    }
  }

  /*This function can be used to update status of the given case alongside its linked cases*/
  @Override
  public void updateLinkedCaseStatus(Long caseId, Long sampleId, String status) {
    List<Cases> linkedCasesList = getAllLinkedCasesInAnr(caseId, false);
    linkedCasesList.forEach(
        cases ->
            changeCaseStatus(cases, CaseStatus.AWAITING_TESTING.name(), requestSession.getUser()));
  }

  @Override
  public void saveToCaseReviewLog(Cases caseData, String caseReviewType, UserMaster user) {
    Optional<CaseReviewLog> reviewLog = getCaseReviewLog(caseData, caseReviewType);
    CaseReviewLog caseReviewLog = reviewLog.orElseGet(CaseReviewLog::new);
    caseReviewLog.setCaseData(caseData);
    caseReviewLog.setReviewer(user);
    caseReviewLog.setReviewedDate(Instant.now());
    caseReviewLog.setReviewType(caseReviewType);
    caseReviewLog.setIsActive(Boolean.TRUE);
    caseReviewLogRepository.save(caseReviewLog);
  }

  @Override
  public Boolean updateSignOutDate(
      UserMaster user, Cases aCase, Date directorSODate, Long faultToleranceId) {
    UpdateCaseDetailRequest updateCaseDetailRequest = new UpdateCaseDetailRequest();
    UpdateCaseDetailRequest.CaseDetailsUpdate caseUpdateData =
        new UpdateCaseDetailRequest.CaseDetailsUpdate();
    if (directorSODate == null) {
      caseUpdateData.setDirectorSODate(null);
    } else {
      caseUpdateData.setDirectorSODate(
          directorSODate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate());
    }
    updateCaseDetailRequest.setData(caseUpdateData);
    List<Report> reports =
        reportService.getReportsByParentIsNull(aCase).stream()
            .peek(report -> report.setSoDate(Instant.now()))
            .collect(Collectors.toList());
    reportRepository.saveAll(reports);
    return callToANCforSignedOutDate(user, aCase, faultToleranceId, updateCaseDetailRequest);
  }

  public Boolean callToANCforSignedOutDate(
      UserMaster user,
      Cases aCase,
      Long faultToleranceId,
      UpdateCaseDetailRequest updateCaseDetailRequest) {
    String endPoint = accessioningBaseURL + "/cases" + aCase.getCaseNumber() + UPDATE_URL;
    ActivityLog activityLog = null;
    String requestPayLoad = null;
    try {
      requestPayLoad = objectMapper.writeValueAsString(updateCaseDetailRequest);
    } catch (JsonProcessingException e) {
      log.error(e.getMessage());
    }
    activityLog =
        activityLogService.createActivityLog(
            new Throwable().getStackTrace()[0], user, requestPayLoad, endPoint);
    activityLog.setEventType(EventType.GET.getEventValue());
    String auth = azureTokenUtils.getAzureTokenOfOutBoundAPI(user);
    try {
      ResponseEntity<AccessionSignedDateResponse> accessionSignedDateResponseResponseEntity =
          accessionWebClient.updateCaseDetails(
              auth, aCase.getCaseNumber(), updateCaseDetailRequest);
      activityLogService.updateActivityLog(activityLog, accessionSignedDateResponseResponseEntity);
      if (accessionSignedDateResponseResponseEntity != null) {
        isApiSuccess = accessionSignedDateResponseResponseEntity.getStatusCode() == HttpStatus.OK;
        if (Boolean.TRUE.equals(isApiSuccess)) {
          integrationFaultTolerance.saveFaultTolerance(
              faultToleranceId,
              aCase.getCaseId(),
              ANC_CASE_UPDATECASEDETAILS.getApiId(),
              FTPattern.UNEXPECTED_ERROR,
              0,
              activityLog,
              isApiSuccess,
              EntityName.CASE);
        }
      }
    } catch (Exception e) {
      integrationFaultTolerance.getFaultTolerance(
          aCase.getCaseId(),
          faultToleranceId,
          activityLog,
          e,
          ANC_CASE_UPDATECASEDETAILS.getApiId(),
          EntityName.CASE);
    }
    return isApiSuccess;
  }

  @Override
  public Boolean updateCaseDueDate(
      UserMaster user, List<Long> groupIdList, Date caseDueDate, Long faultToleranceId) {
    List<Cases> caseList = getCasesInGroupId(groupIdList);
    String auth = azureTokenUtils.getAzureTokenOfOutBoundAPI(user);
    DateFormat dateFormat = new SimpleDateFormat(DATE_TIME_FORMAT_24);
    dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
    if (caseDueDate == null) return true;
    String endPoint =
        accessioningBaseURL
            + "/cases/"
            + groupIdList
            + DELIMITER_SLASH
            + dateFormat.format(caseDueDate);
    String requestPayload = null;
    try {
      requestPayload = objectMapper.writeValueAsString(caseDueDate);
    } catch (JsonProcessingException e) {
      log.error(e.getMessage());
    }
    ActivityLog activityLog =
        activityLogService.createActivityLog(
            new Throwable().getStackTrace()[0], user, requestPayload, endPoint);
    activityLog.setEventType(EventType.GET.getEventValue());
    List<Cases> dueDateUpdatedCases = updateDueDateForGroupInAnr(user, caseList, caseDueDate);

    /*to avoid unwanted api call to anc when there is no change in due date for case*/
    if (!dueDateUpdatedCases.isEmpty()) {
      try {
        accessionWebClient.updateCaseDueDate(
            auth,
            dueDateUpdatedCases.stream().map(Cases::getGroupId).distinct().toList(),
            dateFormat.format(caseDueDate));
        isApiSuccess = true;
        integrationFaultTolerance.saveFaultTolerance(
            faultToleranceId,
            caseList.get(0).getCaseId(),
            ANC_CASE_DUEDATE.getApiId(),
            FTPattern.UNEXPECTED_ERROR,
            0,
            activityLog,
            isApiSuccess,
            EntityName.CASE);
      } catch (Exception e) {
        integrationFaultTolerance.getFaultTolerance(
            caseList.get(0).getCaseId(),
            faultToleranceId,
            activityLog,
            e,
            ANC_CASE_DUEDATE.getApiId(),
            EntityName.CASE);
      }
    }
    return isApiSuccess;
  }

  private List<Cases> updateDueDateForGroupInAnr(
      UserMaster user, List<Cases> caseList, Date caseDueDate) {
    if (caseDueDate == null) return new ArrayList<>();
    List<Cases> filteredCase =
        caseList.stream()
            .filter(
                unfilteredCases ->
                    !DateUtils.checkIfCurrentDateIsLessThanInputDate(
                        unfilteredCases.getCurrentDueDate(), caseDueDate))
            .toList();
    if (filteredCase.isEmpty()) return new ArrayList<>();
    filteredCase.forEach(cases -> cases.setCurrentDueDate(caseDueDate));
    logDelayCaseHistory(filteredCase, caseDueDate, user);
    return caseRepo.saveAll(filteredCase);
  }

  @Override
  public Optional<CaseReviewLog> getCaseReviewLog(Cases caseData, String reviewType) {
    return caseReviewLogRepository.findByCaseDataCaseIdAndReviewTypeAndIsActiveTrue(
        caseData.getCaseId(), reviewType);
  }

  /**
   * Method will change case current status to PHD_REVIEW
   *
   * @param aCase
   * @param user
   * @return Case
   */
  @Override
  public Cases updateCaseStatusToPHDReview(Cases aCase, CaseStatus caseStatus, UserMaster user) {
    aCase.setCaseStatus(caseStatus);
    aCase.setCaseProgressStatus(UN_ASSIGNED);
    aCase.setCaseAssignee(null);
    aCase.setModifiedUser(user);

    List<CaseIndicatorMapping> sampleRDOIndicators =
        getIndicatorsOfCaseIdAndSlug(aCase.getCaseId(), SAMPLE_RDO);

    sampleRDOIndicators.addAll(getIndicatorsOfCaseIdAndSlug(aCase.getCaseId(), SAMPLE_RDO_EXT_LAB));
    if (!sampleRDOIndicators.isEmpty()) {
      Optional<CaseReviewLog> optionalCaseReviewLog =
          caseReviewLogRepository.findByCaseDataCaseIdAndReviewTypeAndIsActiveTrue(
              aCase.getCaseId(), PRIMARY_REVIEW_TYPE);
      if (optionalCaseReviewLog.isPresent()) {
        UserMaster reviewer = optionalCaseReviewLog.get().getReviewer();
        if (reviewer != null) {
          if (Objects.equals(reviewer.getUserId(), user.getUserId())) {
            throw new BadRequestException(errorMessage.getCantProceedToPhd());
          }
        } else {
          saveToCaseReviewLog(aCase, SECONDARY_DATA_REVIEW, user);
        }
      }
    } else {
      saveToCaseReviewLog(aCase, CASE_REVIEW_TYPE_DATA, user);
    }
    boolean roleLabManager = userService.isUserInParticularRole(user, LAB_MANAGER_ROLE);
    if (roleLabManager) saveToCaseReviewLog(aCase, LAB_MANAGER_REVIEW, user);
    saveCaseHistory(aCase.getCaseId(), user, caseStatus.getStatus());
    Cases cases = caseRepo.save(aCase);
    piSupport.setCaseNumber(cases.getCaseNumber());
    piSupport.setCaseUpdate(true);
    return cases;
  }

  /**
   * @param caseId - for fetching linked cases.
   * @param user - to track the user.
   * @return list of casePhdResponse. update cases status of case to PhD review as a group and
   *     returns current case status and progress status of all linked cases moved to PhD review.
   *     Case assignee will always be null on the response.
   */
  @Override
  public List<CasePHDResponse> updateCaseStatusToPhdReviewAsGroup(Long caseId, UserMaster user) {
    List<CasePHDResponse> casePHDResponseList = new ArrayList<>();
    getAllLinkedCasesInAnr(caseId, false)
        .forEach(
            cases -> {
              Cases aCase = updateCaseStatusToPHDReview(cases, CaseStatus.PHD_REVIEW, user);
              casePHDResponseList.add(
                  CasePHDResponse.builder()
                      .caseStatus(aCase.getCaseStatus())
                      .caseProgressStatus(aCase.getCaseProgressStatus())
                      .message(successMessage.getUpdatedCaseStatus())
                      .build());
            });
    return casePHDResponseList;
  }

  @Override
  public List<CasePHDResponse> updateCaseStatusToSupervisorReviewAsGroup(
      Long caseId, UserMaster user) {
    List<CasePHDResponse> casePHDResponseList = new ArrayList<>();
    getAllLinkedCasesInAnr(caseId, false)
        .forEach(
            cases -> {
              Cases aCase = updateCaseStatusToPHDReview(cases, CaseStatus.SUPERVISOR_REVIEW, user);
              casePHDResponseList.add(
                  CasePHDResponse.builder()
                      .caseStatus(aCase.getCaseStatus())
                      .caseProgressStatus(aCase.getCaseProgressStatus())
                      .message(successMessage.getUpdatedCaseStatus())
                      .build());
            });
    return casePHDResponseList;
  }

  /**
   * @param caseId - for fetching linked cases.
   * @param user - to track the user.
   * @return list of casePhdResponse.
   */
  @Override
  public List<CasePHDResponse> updateCaseStatusToSignatureReadyAsGroup(
      Long caseId, UserMaster user) {
    List<CasePHDResponse> casePHDResponseList = new ArrayList<>();
    List<Cases> allLinkedCasesInAnr = getAllLinkedCasesInAnr(caseId, false);
    boolean isAllCasesAreApproved =
        allLinkedCasesInAnr.stream()
            .anyMatch(
                aCase -> {
                  boolean caseIsSTR =
                      aCase.getTestTypeSubCategory().getId() == STR_TEST_TYPE_ID
                          || aCase.getTestTypeSubCategory().getId()
                              == PIConstants.YSTR_TEST_TYPE_ID;
                  List<CaseApproval> caseApprovals =
                      caseApprovalRepository.findCaseApprovalByCaseId(aCase);
                  return caseApprovals.stream()
                      .anyMatch(
                          approval ->
                              (approval.getIsInterpretationApproved() || caseIsSTR)
                                  && (approval.getIsPICApproved() || caseIsSTR)
                                  && approval.getIsReportApproved()
                                  && approval.getIsResultApproved());
                });
    if (!isAllCasesAreApproved) throw new BadRequestException("Pending case approvals!");
    allLinkedCasesInAnr.forEach(
        aCase -> {
          aCase.setCaseStatus(CaseStatus.SIGNATURE_QUEUE);
          aCase.setModifiedUser(user);
          saveCaseHistory(aCase.getCaseId(), user, CaseStatus.SIGNATURE_QUEUE.getStatus());
          casePHDResponseList.add(
              CasePHDResponse.builder()
                  .caseStatus(aCase.getCaseStatus())
                  .caseProgressStatus(aCase.getCaseProgressStatus())
                  .message(successMessage.getUpdatedCaseStatus())
                  .build());
        });
    caseRepo.saveAll(allLinkedCasesInAnr);
    return casePHDResponseList;
  }

  /**
   * ` This method will save case communication logs
   *
   * @param aCase
   * @param caseCommunicationRequest
   * @param user
   */
  @Override
  public void logCommunication(
      Cases aCase, CaseCommunicationRequest caseCommunicationRequest, UserMaster user) {
    var caseCommunicationLogs = new CaseCommunication();
    caseCommunicationLogs.setComment(caseCommunicationRequest.getCommunicationLog());
    caseCommunicationLogs.setCreatedUser(user);
    caseCommunicationLogs.setModifiedUser(user);
    caseCommunicationLogs.setCaseId(aCase);
    caseCommunicationLogsRepository.save(caseCommunicationLogs);
  }

  /**
   * Getting all case communication log by caseId
   *
   * @param aCase
   * @return
   */
  @Override
  public List<CaseCommunication> getCaseCommunicationLogsByCase(Cases aCase) {
    Sort sort = Sort.by(DESC, AbstractAuditingEntity_.MODIFIED_DATE);
    return caseCommunicationLogsRepository.findByCaseId(aCase, sort);
  }

  /**
   * Save case history
   *
   * @param caseId
   * @param currentUser
   * @param caseStatusString
   * @return CaseStatusChangelog
   */
  @Override
  public CaseStatusChangelog saveCaseHistory(
      Long caseId, UserMaster currentUser, String caseStatusString) {
    Cases aCase = getCase(caseId);
    com.ddc.analysis.entity.CaseStatus caseStatus =
        caseStatusRepository
            .findByCaseStatus(caseStatusString)
            .orElseThrow(
                () ->
                    new CaseTypeNotFoundException(
                        format(errorMessage.getCaseTypeNotFound(), caseStatusString)));
    UserMaster user = null;
    if (currentUser != null) user = userService.getUser(currentUser.getUserId());
    UserRoleMapping uSerRoleMapping = null;
    if (user != null) uSerRoleMapping = userService.getUserRoleMapping(user.getUserId());
    var caseStatusChangeLog = new CaseStatusChangelog();
    caseStatusChangeLog.setCaseId(aCase);
    caseStatusChangeLog.setModifiedUser(user);
    caseStatusChangeLog.setCaseStatus(caseStatus);
    caseStatusChangeLog.setModifiedUser(user);
    caseStatusChangeLog.setUserRole(uSerRoleMapping);
    return caseStatusChangelogRepository.saveAndFlush(caseStatusChangeLog);
  }

  /**
   * Get case history by caseId
   *
   * @param aCase
   * @return List<CaseStatusChangelog>
   */
  @Override
  public List<CaseStatusChangelog> getCaseHistory(Cases aCase) {
    Sort sort = Sort.by(DESC, AbstractAuditingEntity_.MODIFIED_DATE);
    return caseStatusChangelogRepository.findByCaseId(aCase, sort);
  }

  /**
   * Get case history by caseId for a specific status
   *
   * @param aCase
   * @return List<CaseStatusChangelog>
   */
  @Override
  public CaseStatusChangelog getCaseHistoryModifiedDateForCaseWithStatus(
      Cases aCase, String caseStatus) {
    com.ddc.analysis.entity.CaseStatus caseStatusOp = null;
    Optional<com.ddc.analysis.entity.CaseStatus> caseStatusOptional =
        caseStatusRepository.findByCaseStatus(caseStatus);
    if (caseStatusOptional.isPresent()) caseStatusOp = caseStatusOptional.get();
    return caseStatusChangelogRepository.findFirstByCaseIdAndCaseStatusOrderByModifiedDateDesc(
        aCase, caseStatusOp);
  }

  /**
   * Method will save CaseApproval
   *
   * @param caseApproval
   * @return CaseApproval
   */
  @Override
  public CaseApproval approvalOfCaseReport(CaseApproval caseApproval) {
    return caseApprovalRepository.save(caseApproval);
  }

  /**
   * Method will get CaseApproval by caseApprovalId
   *
   * @param caseApprovalId
   * @return CaseApproval
   */
  @Override
  public CaseApproval getCaseApprovalById(Integer caseApprovalId) {
    return caseApprovalRepository
        .findById(caseApprovalId)
        .orElseThrow(
            () ->
                new CaseApprovalNotFound(
                    format(errorMessage.getCaseApprovalNotFound(), caseApprovalId)));
  }

  /**
   * Its Reject and assign back, all case approval status will change to false by caseId, And case
   * status change to REVIEW_REQUIRED
   *
   * @param caseApproval
   * @param user
   * @param aCase
   */
  @Override
  public void rejectCaseReport(CaseApproval caseApproval, UserMaster user, Cases aCase) {
    if (aCase.getCaseStatus() == CaseStatus.CASE_CLOSED)
      throw new BadRequestException(
          format(errorMessage.getCaseAlreadyClosed(), aCase.getCaseNumber()));
    //    if (!aCase.getIsNippTestType()) {
    //      aCase.setCaseStatus(CaseStatus.REVIEW_REQUIRED);
    //    }
    aCase.setCaseStatus(CaseStatus.SUPERVISOR_REVIEW);
    aCase.setCaseProgressStatus(UN_ASSIGNED);
    aCase.setCaseAssignee(null);
    aCase.setModifiedUser(user);
    caseRepo.save(aCase);
    Optional<CaseReviewLog> phdPrimaryReviewerOptional =
        caseReviewLogRepository.findByCaseDataCaseIdAndReviewTypeAndIsActiveTrue(
            aCase.getCaseId(), PRIMARY_PHD_REVIEW);
    if (phdPrimaryReviewerOptional.isPresent()) {
      caseReviewLogRepository.delete(phdPrimaryReviewerOptional.get());
    }
    saveCaseHistory(aCase.getCaseId(), user, CaseStatus.REVIEW_REQUIRED.getStatus());
    if (caseApproval != null) caseApprovalRepository.save(caseApproval);
  }

  /**
   * Getting case approval by Case and Report
   *
   * @param aCase
   * @param report
   * @return Optional<CaseApproval>
   */
  @Override
  public Optional<CaseApproval> getCaseApprovalByCaseAndReport(Cases aCase, Report report) {
    return caseApprovalRepository.findByCaseIdAndReport(aCase, report);
  }

  /**
   * In this method will case status to MOVED_TO_CLOSE/CASE_CLOSED its depending on isCaseClose flag
   *
   * @param aCase
   * @param isCaseClose
   * @param user
   * @return Case
   */
  @Transactional(isolation = Isolation.READ_UNCOMMITTED)
  @Override
  public Cases proceedToClose(Cases aCase, boolean isCaseClose, UserMaster user) {
    List<CaseApproval> caseApprovals = caseApprovalRepository.findCaseApprovalByCaseId(aCase);
    validateCaseForClosing(caseApprovals, aCase.getTestTypeSubCategory(), aCase.getCaseId(), user);
    boolean phdAlreadySigned = getCaseReviewLog(aCase, CASE_REVIEW_TYPE_PHD).isPresent();
    if (!isCaseClose) {
      aCase.setCaseStatus(CaseStatus.MOVED_TO_CLOSE);
      if (!phdAlreadySigned) saveToCaseReviewLog(aCase, CASE_REVIEW_TYPE_PHD, user);
      saveCaseHistory(aCase.getCaseId(), user, CaseStatus.MOVED_TO_CLOSE.getStatus());
    } else {
      aCase.setCaseStatus(CaseStatus.CASE_CLOSED);
      aCase.setCaseAssignee(null);
      aCase.setCaseProgressStatus(UN_ASSIGNED);
      if (!phdAlreadySigned) saveToCaseReviewLog(aCase, CASE_REVIEW_TYPE_PHD, user);
      saveCaseHistory(aCase.getCaseId(), user, CaseStatus.CASE_CLOSED.getStatus());
      long parsedCaseNumber;
      try {
        parsedCaseNumber = Long.parseLong(aCase.getCaseNumber());
      } catch (NumberFormatException e) {
        throw new BadRequestException(errorMessage.getCaseNumberShouldContainNumericValues());
      }
      updateCaseStatusToAccessioning(aCase, user, parsedCaseNumber, CASE_CLOSED_SLUG, null);
    }
    aCase.setModifiedUser(user);
    Cases cases = caseRepo.save(aCase);
    piSupport.setCaseNumber(cases.getCaseNumber());
    piSupport.setCaseUpdate(true);
    return cases;
  }

  public Boolean updateCaseStatusToAccessioning(
      Cases aCase, UserMaster user, long parsedCaseNumber, String slug, Long faultToleranceId) {
    String requestPayLoad = null;
    CaseStatusPayload caseStatusPayload = new CaseStatusPayload();
    caseStatusPayload.setParsedCaseNumber(parsedCaseNumber);
    caseStatusPayload.setSlug(slug);
    String endPoint =
        accessioningBaseURL
            + updateCaseStatus
                .replace("{caseId}", aCase.getCaseNumber())
                .replace("{caseStatus}", slug);
    try {
      requestPayLoad = objectMapper.writeValueAsString(caseStatusPayload);
    } catch (JsonProcessingException e) {
      e.printStackTrace();
    }
    ActivityLog activityLog =
        activityLogService.createActivityLog(
            new Throwable().getStackTrace()[0], user, requestPayLoad, endPoint);
    activityLog.setEventType(EventType.GET.getEventValue());
    String auth = azureTokenUtils.getAzureTokenOfOutBoundAPI(user);
    ResponseEntity<AccessioningBasicResponse> responseEntity;
    try {
      responseEntity = accessionWebClient.updateCaseStatus(auth, parsedCaseNumber, slug);
      activityLogService.updateActivityLog(activityLog, responseEntity);
      if (responseEntity != null) {
        isApiSuccess = responseEntity.getStatusCode() == HttpStatus.OK;
        if (Boolean.TRUE.equals(isApiSuccess)) {
          integrationFaultTolerance.saveFaultTolerance(
              faultToleranceId,
              aCase.getCaseId(),
              ANC_CASE_STATUS.getApiId(),
              FTPattern.UNEXPECTED_ERROR,
              0,
              activityLog,
              isApiSuccess,
              EntityName.CASE);
        }
      }
    } catch (Exception e) {
      integrationFaultTolerance.getFaultTolerance(
          aCase.getCaseId(),
          faultToleranceId,
          activityLog,
          e,
          ANC_CASE_STATUS.getApiId(),
          EntityName.CASE);
    }
    return isApiSuccess;
  }

  /**
   * @param caseChangeActionRequest payload containing change action and test id's of cases to
   *     update.
   * @since last update, PI_UPDATE action was updated to only recreate pi and update case details,
   *     re-generating report and report history functionality was removed for this action. !!!!This
   *     function assumes all the testIds in the request belongs to the same group id!!!!!.
   */
  @Override
  @Transactional
  public void updateCase(CaseChangeActionRequest caseChangeActionRequest) {
    List<Cases> caseListForPerformingCaseChangeAction =
        !caseChangeActionRequest.getTestIds().isEmpty()
            ? caseRepo.findByCaseNumberIn(
                caseChangeActionRequest.getTestIds().stream().map(Object::toString).toList())
            : caseRepo.findByGroupId(Long.valueOf(caseChangeActionRequest.getCaseNumber()));
    if (caseListForPerformingCaseChangeAction.isEmpty()) {
      throw new BadRequestException(errorMessage.getCaseNotFound());
    }
    caseListForPerformingCaseChangeAction.forEach(
        caseData -> {
          String changeAction = caseChangeActionRequest.getChangeAction();
          piSupport.setRecalculate(true);
          piSupport.setCaseChangeAction(caseChangeActionRequest.getChangeAction());
          Optional<CaseReviewLog> caseReviewLog =
              getCaseReviewLog(caseData, CASE_REVIEW_TYPE_NOTARY);
          caseReviewLog.ifPresent(reviewLog -> caseReviewLogRepository.delete(reviewLog));
          /*this condition checks for whether the requested change action need a
          regeneration of report and pi-recalculation */
          if (caseChangeActionHasReportGeneration(changeAction)) {
            recalculateAndRegenerateReport(caseChangeActionRequest, caseData);
          } else {
            switch (changeAction) {
              case CASE_CHANGE_RESIGN -> updateCaseStatusAndLogCaseChangeAction(
                  caseChangeActionRequest, caseData);
              case CASE_CHANGE_REPRINT -> updateCaseDataAndChangeStatus(
                  caseChangeActionRequest, caseData);
              case CASE_CHANGE_AWAITING -> updateCaseDataAndChangeStatusToAwaitingTesting(
                  caseChangeActionRequest, caseData);
              case CASE_CHANGE_CASE_UPDATE, CASE_UNLINK -> updateCaseData(
                  caseChangeActionRequest, caseData);
              case CASE_AUDITED -> updateCaseDetailsAndProceedToPI(
                  caseChangeActionRequest, caseData);
              case CASE_CHANGE_REPRINT_WITH_FETUS_GENDER_CHANGE -> updateFetusGenderAndReprint(
                  caseChangeActionRequest, caseData);
              case CASE_CHANGE_UPDATE_PI -> reCalculatePi(caseChangeActionRequest, caseData);
              case CASE_REACTIVATE_SAMPLE -> reactivateSample(caseData, caseChangeActionRequest);
              default -> throw new BadRequestException(errorMessage.getInvalidCaseChangeAction());
            }
          }
        });

    /*to trigger calculation after re-activation of all case sample mappings under the groupId*/
    reCalculateAfterReactivation(caseChangeActionRequest, caseListForPerformingCaseChangeAction);
    caseListForPerformingCaseChangeAction.forEach(
        caseData -> piHelper.setModifiedStatusOfCase(caseData, caseChangeActionRequest));
  }

  private void updateFetusGenderAndReprint(
      CaseChangeActionRequest caseChangeActionRequest, Cases caseData) {
    if (caseData.getCaseStatus() != CaseStatus.AWAITING_TESTING
        && caseData.getCaseStatus() != CaseStatus.NOT_READY
        && caseData.getCaseStatus() != CaseStatus.HOLD
        && caseData.getCaseStatus() != CaseStatus.CANCELED) {
      caseData.setCaseStatus(CaseStatus.REVIEW_REQUIRED);
    }

    logReportHistory(
        caseData,
        reportService.getReportsByCase(caseData),
        caseChangeActionRequest.getChangeAction());
    updateCaseDetails(caseData, null);
    if (!isOnHoldOrCancelled(caseData.getCaseStatus())) {
      caseData.setCaseStatus(CaseStatus.PHD_REVIEW);
    }
    List<Report> reports = reportService.getReportsByParentIsNull(caseData);
    reconstructBasedStatusChange(caseData, reports.stream().findFirst().orElse(null));
    caseRepo.save(caseData);
    piCalculationFactory.reCreatePI(caseData, null);
    saveCaseChangeRequest(caseData, caseChangeActionRequest);
  }

  /**
   * make sure this function is called outside the case loop of change action to make sure all the
   * case sample mappings under the group id is verified before triggering the calculation.
   *
   * @param caseChangeActionRequest - to get the change action
   * @param caseListForPerformingCaseChangeAction - to get a case under the groupId
   */
  private void reCalculateAfterReactivation(
      CaseChangeActionRequest caseChangeActionRequest,
      List<Cases> caseListForPerformingCaseChangeAction) {
    try {
      if (caseChangeActionRequest.getChangeAction().equalsIgnoreCase(CASE_REACTIVATE_SAMPLE)
          && piSupport.isReActivationRoutineCompleted()) {
        recalculateAndUpdateCaseStatus(caseListForPerformingCaseChangeAction.get(0));
      } else {
        piService.syncCaseStatusAcrossLinkedCases(caseListForPerformingCaseChangeAction, null);
      }
    } catch (IndexOutOfBoundsException ie) {
      log.error(ie.getMessage());
    }
  }

  /**
   * re-activates inactive samples and update redraw requests against the sample to completed, and
   * recalculates case. if all the samples are activated from anc side; recalculates the case, if
   * not only the samples that are activated from ANC will be activated and re-calculation does NOT
   * happen in that scenario.
   *
   * @param caseData - for fetching case sample mapping
   */
  private void reactivateSample(Cases caseData, CaseChangeActionRequest caseChangeActionRequest) {
    if (caseData.getCaseStatus() == CaseStatus.CASE_CLOSED) return;
    piSupport.setRecalculate(false);
    List<AssertionSampleResponse> sampleDetailsFromANC = null;
    Map<String, String> sampleStatusMap = null;

    try {
      sampleDetailsFromANC = getAssertionSampleResponses(caseData);
    } catch (Exception e) {
      log.error(e.getMessage());
      throw new ServerException(
          "Something went wrong with fetching case details, please refer log data");
    }

    sampleStatusMap = generateSampleSampleStatusMap(sampleDetailsFromANC);

    for (CaseSampleMapping caseSampleMapping : caseData.getCaseSampleMapping()) {
      if (checkSampleStatusIsActive(caseSampleMapping, sampleStatusMap)) {
        reActivateAndSetRedrawCompleted(caseData, caseSampleMapping);
      }
    }

    piSupport.setReActivationRoutineCompleted(
        sampleDetailsFromANC.stream()
            .noneMatch(
                sample -> sample.getSampleStatusId() == null || sample.getSampleStatusId() == 3));
    List<Report> reports = reportService.getReportsByParentIsNull(caseData);
    reconstructBasedStatusChange(caseData, reports.stream().findFirst().orElse(null));
    updateCaseDetails(caseData, null);
    caseRepo.save(caseData);
    saveCaseChangeRequest(caseData, caseChangeActionRequest);
  }

  @NotNull
  private static Map<String, String> generateSampleSampleStatusMap(
      List<AssertionSampleResponse> sampleDetailsFromANC) {
    Map<String, String> sampleStatusMap;
    sampleStatusMap =
        sampleDetailsFromANC.stream()
            .collect(
                Collectors.toMap(
                    sample ->
                        sample.getSampleNumber() != null ? sample.getSampleNumber() : INACTIVE,
                    sample ->
                        sample.getSampleStatusId() != null && sample.getSampleStatusId() == 1
                            ? ACTIVE
                            : INACTIVE,
                    (existingValue, newValue) -> newValue,
                    HashMap::new));

    return sampleStatusMap;
  }

  private void reActivateAndSetRedrawCompleted(
      Cases caseData, CaseSampleMapping caseSampleMapping) {
    Samples sample = caseSampleMapping.getSamples();
    caseSampleMapping.setActive(true);
    if (sample.getSampleRunStatus() != SampleRunStatus.RE_PROCESS_REQUESTED)
      sample.setSampleRunStatus(SampleRunStatus.NEW);
    RedrawData redrawData =
        redrawDataRepository.findBySamplesAndCasesAndIsCompletedNot(sample, caseData, true);
    if (redrawData != null) {
      redrawData.setIsCompleted(true);
      redrawDataRepository.save(redrawData);
      sampleRepository.save(sample);
    }
  }

  private boolean checkSampleStatusIsActive(
      CaseSampleMapping caseSampleMapping, Map<String, String> sampleStatusMap) {
    if (sampleStatusMap != null
        && sampleStatusMap.containsKey(caseSampleMapping.getSamples().getSampleCode())) {
      return sampleStatusMap
          .get(caseSampleMapping.getSamples().getSampleCode())
          .equalsIgnoreCase(ACTIVE_SAMPLE_SLUG);
    }
    return false;
  }

  private List<AssertionSampleResponse> getAssertionSampleResponses(Cases caseData) {
    List<AssertionSampleResponse> sampleDetailsFromANC = null;
    String auth = azureTokenUtils.getAzureTokenOfOutBoundAPI(null);
    ResponseEntity<AccessioningBasicResponse> accessionResponse =
        accessioningFeignClient.getCaseDetails(auth, Long.valueOf(caseData.getCaseNumber()));
    AccessioningBasicResponse body = accessionResponse.getBody();
    if (body != null) {
      sampleDetailsFromANC = body.getData().getCaseDetails().getSamples();
    }
    return sampleDetailsFromANC != null ? sampleDetailsFromANC : new ArrayList<>();
  }

  /**
   * if the case doesn't contain plate ID that means the PI haven't completed for the case, in this
   * scenario - no recalculation occurs. null pointer exception points that pi is not completed for
   * the plate
   *
   * @param caseData case data
   */
  private void recalculateAndUpdateCaseStatus(Cases caseData) {
    try {
      piService.caseRecalculatePi(
          caseData.getPlate().getPlateId(),
          getAllLinkedCasesInAnr(caseData.getCaseId(), false).stream()
              .map(Cases::getCaseId)
              .toList());
    } catch (NullPointerException e) {
      log.error("[REACTIVATE] Plate PI calculation Pending - {}", caseData.getCaseId());
    }
  }

  private void updateCaseData(CaseChangeActionRequest caseChangeActionRequest, Cases caseData) {
    updateCaseDetails(caseData, null);
    caseData = caseRepo.getByCaseId(caseData.getCaseId());
    if (caseChangeActionRequest.getChangeAction().equalsIgnoreCase(CASE_UNLINK)) {
      caseData.setCaseAssignee(null);
      caseData.setCaseProgressStatus(UN_ASSIGNED);
    }
    List<Report> reports = reportService.getReportsByParentIsNull(caseData);
    reconstructBasedStatusChange(caseData, reports.stream().findFirst().orElse(null));
    caseRepo.save(caseData);
    saveCaseChangeRequest(caseData, caseChangeActionRequest);
  }

  private void updateCaseDetailsAndProceedToPI(
      CaseChangeActionRequest caseChangeActionRequest, Cases caseData) {
    updateCaseDetails(caseData, null);
    caseData = getCase(caseData.getCaseId());
    saveCaseChangeRequest(caseData, caseChangeActionRequest);
    if (caseData.getPlate() != null) {
      Long plateId = caseData.getPlate().getPlateId();
      Optional<PlateHistory> lastPlateActivity =
          plateService.getPlateHistory(caseData.getPlate().getPlateId()).stream().findFirst();
      piSupport.setRecalculate(false);
      lastPlateActivity.ifPresent(
          plateHistory -> plateService.proceedToPi(plateId, plateHistory.getUser()));
    }
    log.info("CASE_AUDIT - PLATE PI NOT CALCULATED - audit status changed");
  }

  private void reCalculatePi(CaseChangeActionRequest caseChangeActionRequest, Cases caseData) {
    if (caseData.getCaseStatus() == CaseStatus.CASE_CLOSED) return;
    clearCaseReviewLog(List.of(caseData.getCaseId()));
    labVantageIntegrationService.clearSubmittedCheckList(caseData);
    updateCaseDetails(caseData, null);
    if (caseData.getCaseStatus() != CaseStatus.AWAITING_TESTING
        && caseData.getCaseStatus() != CaseStatus.NOT_READY
        && caseData.getCaseStatus() != CaseStatus.HOLD
        && caseData.getCaseStatus() != CaseStatus.CANCELED
        && !caseData.getIsNippTestType()
        && !caseData.getIsSnpTestType()) {
      caseData.setCaseStatus(CaseStatus.REVIEW_REQUIRED);
    }
    List<Report> reports = reportService.getReportsByParentIsNull(caseData);
    reconstructBasedStatusChange(caseData, reports.stream().findFirst().orElse(null));
    caseRepo.save(caseData);
    if (caseData.getCaseStatus() != CaseStatus.NOT_READY
        && caseData.getCaseStatus() != CaseStatus.HOLD
        && caseData.getCaseStatus() != CaseStatus.CANCELED) {
      piCalculationFactory.reCreatePI(caseData, null);
      saveCaseChangeRequest(caseData, caseChangeActionRequest);
      updateCaseGroupStatus(
          caseData,
          false,
          userService.parseUserFromToken(request),
          piSupport.getCaseChangeAction());
    }
  }

  private void clearCaseReviewLog(List<Long> caseIds) {
    caseIds.forEach(
        caseId -> {
          List<Cases> allLinkedCasesInAnr = getAllLinkedCasesInAnr(caseId, true);
          List<CaseReviewLog> caseReviewLogs =
              caseReviewLogRepository.findByCaseDataCaseIdInAndIsActiveTrue(
                  allLinkedCasesInAnr.stream().map(Cases::getCaseId).toList());
          caseReviewLogs.forEach(cr -> cr.setIsActive(Boolean.FALSE));
          caseReviewLogRepository.saveAll(caseReviewLogs);
        });
  }

  private void updateCaseDataAndChangeStatusToAwaitingTesting(
      CaseChangeActionRequest caseChangeActionRequest, Cases caseData) {
    labVantageIntegrationService.clearSubmittedCheckList(caseData);
    updateCaseDetails(caseData, null);
    if (!isOnHoldOrCancelled(caseData.getCaseStatus())
        && !caseData.getCaseStatus().equals(CaseStatus.NOT_READY)) {
      caseData.setCaseStatus(CaseStatus.AWAITING_TESTING);
      caseData.setOriginalCaseStatus(CaseStatus.AWAITING_TESTING);
    }
    if (caseData.getCaseStatus() == CaseStatus.AWAITING_TESTING) {
      List<ReportMarker> activeReportMarkers =
          reportMarkerRepository.findByCaseIdAndIsUsed(caseData.getCaseId(), Boolean.TRUE);
      activeReportMarkers.forEach(rm -> rm.setIsUsed(Boolean.FALSE));

      /*this report list will contain one report most of the time, so no need to use saveAll on the
      report list to optimise db calls*/
      List<Report> reports = reportService.getReportsByParentIsNull(caseData);
      reports.forEach(report -> reportRepository.save(cpiService.clearReportCalculation(report)));
      reportMarkerRepository.saveAll(activeReportMarkers);
    }
    caseQCFactory.setPreviousCaseAlertInactive(caseData);
    caseRepo.save(caseData);
    saveCaseChangeRequest(caseData, caseChangeActionRequest);
  }

  private void recalculateAndRegenerateReport(
      CaseChangeActionRequest caseChangeActionRequest, Cases caseData) {
    clearCaseReviewLog(List.of(caseData.getCaseId()));
    labVantageIntegrationService.clearSubmittedCheckList(caseData);
    piSupport.setChangeCaseAction(true);
    caseData.setCaseProgressStatus(UN_ASSIGNED);
    caseData.setCaseAssignee(null);
    caseApprovalRepository.deleteByCaseId(caseData);
    List<Report> reportsByCase = reportService.getReportsByCase(caseData);
    // TODO Optimization required
    if (List.of(CASE_CHANGE_AMEND, CASE_CHANGE_AMEND_WITH_INTERPRETATION_ERROR)
        .contains(caseChangeActionRequest.getChangeAction())) {
      for (var report : reportsByCase) {
        final Long ENGLISH_ID = 1L;
        List<ReportInterpretation> reportInterpretations =
            reportInterpretationRepository.findByReportIdAndLanguageIdLanguageId(
                report, ENGLISH_ID);
        if (!reportInterpretations.isEmpty()) {
          String interpretationText = reportInterpretations.get(0).getInterpretation();
          String patternString = "Note:(?s)(.*?)RN: .\\d*.";
          Pattern pattern = Pattern.compile(patternString);
          Matcher matcher = pattern.matcher(interpretationText);
          List<String> validDisclaimers = new ArrayList<>();
          while (matcher.find()) {
            validDisclaimers.add(matcher.group());
          }
          report.setAdditionalDisclaimer(
              validDisclaimers.stream().collect(Collectors.joining(",")));
          reportRepository.save(report);
        }
      }
    }

    logReportHistory(caseData, reportsByCase, caseChangeActionRequest.getChangeAction());
    updateCaseDetails(caseData, null);
    if (caseChangeActionRequest.getChangeAction().equals(CASE_CHANGE_CASE_DEFINITION)
        || caseChangeActionRequest.getChangeAction().equals(CASE_DEF_UPDATE_WITH_STATUS_CHANGE)) {
      piSupport.setActiveReportTestType(caseData.getTestTypeSubCategory());
    }

    saveCaseChangeRequest(caseData, caseChangeActionRequest);
    regenerateReport(caseData);
    List<Report> reports = reportService.getReportsByParentIsNull(caseData);
    reconstructBasedStatusChange(caseData, reports.stream().findFirst().orElse(null));
    caseRepo.save(caseData);
    if (CASE_DEF_UPDATE_WITH_STATUS_CHANGE.equalsIgnoreCase(piSupport.getCaseChangeAction())
        && !isOnHoldOrCancelled(caseData.getCaseStatus())
        && !caseData.getCaseStatus().equals(CaseStatus.NOT_READY)) {
      caseData.setCaseStatus(CaseStatus.AWAITING_TESTING);
      caseRepo.save(caseData);
    }
  }

  private void updateCaseDataAndChangeStatus(
      CaseChangeActionRequest caseChangeActionRequest, Cases caseData) {
    logReportHistory(
        caseData,
        reportService.getReportsByCase(caseData),
        caseChangeActionRequest.getChangeAction());
    updateCaseDetails(caseData, null);
    if (!isOnHoldOrCancelled(caseData.getCaseStatus())) {
      caseData.setCaseStatus(CaseStatus.PHD_REVIEW);
    }
    List<Report> reports = reportService.getReportsByParentIsNull(caseData);
    reconstructBasedStatusChange(caseData, reports.stream().findFirst().orElse(null));
    caseRepo.save(caseData);
    saveCaseChangeRequest(caseData, caseChangeActionRequest);
  }

  private void updateCaseStatusAndLogCaseChangeAction(
      CaseChangeActionRequest caseChangeActionRequest, Cases caseData) {
    if (!isOnHoldOrCancelled(caseData.getCaseStatus())) {
      caseData.setCaseStatus(CaseStatus.PHD_REVIEW);
    }
    List<Report> reports = reportService.getReportsByParentIsNull(caseData);
    reconstructBasedStatusChange(caseData, reports.stream().findFirst().orElse(null));
    caseRepo.save(caseData);
    List<Report> reportsByCase = reportService.getReportsByCase(caseData);
    inactivateReportToClosing(reportsByCase);
    caseApprovalRepository.deleteByReport(reportsByCase.stream().map(Report::getId).toList());
    saveCaseChangeRequest(caseData, caseChangeActionRequest);
  }

  private boolean caseChangeActionHasReportGeneration(String changeAction) {
    return changeAction.equals(CASE_CHANGE_CASE_DEFINITION)
        || changeAction.equals(CASE_CHANGE_AMEND)
        || changeAction.equals(CASE_CHANGE_AMEND_WITH_INTERPRETATION_ERROR)
        || changeAction.equals(CASE_CHANGE_REPRINT_WITH_INTERPRETATION_ERROR)
        || changeAction.equals(CASE_DEF_UPDATE_WITH_STATUS_CHANGE);
  }

  private void regenerateReport(Cases caseData) {
    List<Report> reportsByCase = reportService.getReportsByCase(caseData);
    if (caseData.getCaseStatus() != CaseStatus.NOT_READY
        && !isOnHoldOrCancelled(caseData.getCaseStatus())) {
      //      performPlateQc(caseData.getCaseId());
      piCalculationFactory.reCreatePI(caseData, null);
      reportService.deleteSavedInterpretationAndDisclaimers(reportsByCase);
    }
  }

  /*when case is amended reports are deleted, reports without parents are saved
  against the caseId for referring previous reportId*/
  private void logReportHistory(
      Cases caseData, List<Report> reportsByCase, String caseChangeAction) {
    List<ReportToClosing> distinctReportToClosingList = inactivateReportToClosing(reportsByCase);
    reportsByCase.forEach(
        report -> {
          if (report.getParent() == null) {
            ReportHistory reportHistory = new ReportHistory();
            reportHistory.setCaseId(caseData.getCaseId());
            reportHistory.setReportId(report.getId());
            reportHistory.setClosedHistoryReports(
                distinctReportToClosingList.stream()
                    .map(reportToClosing -> reportToClosing.getId().toString())
                    .collect(Collectors.joining(",")));
            reportHistory.setCaseChangeAction(caseChangeAction);
            reportHistoryRepository.save(reportHistory);
            if (!caseChangeAction.equalsIgnoreCase(CASE_CHANGE_REPRINT)
                && !caseChangeAction.equalsIgnoreCase(
                    CASE_CHANGE_REPRINT_WITH_FETUS_GENDER_CHANGE)) {
              createCopyOfReport(report);
              report.setActive(Boolean.FALSE);
              reportRepository.save(report);
            }
          }
        });

    if (!caseChangeAction.equalsIgnoreCase(CASE_CHANGE_REPRINT)
        && !caseChangeAction.equalsIgnoreCase(CASE_CHANGE_REPRINT_WITH_FETUS_GENDER_CHANGE)) {
      List<Report> oldReports =
          distinctReportToClosingList.stream().map(ReportToClosing::getReport).toList();
      oldReports.forEach(r -> r.setActive(Boolean.FALSE));
      reportRepository.saveAll(oldReports);
    }
  }

  @Override
  public Report createCopyOfReport(Report report) {
    if (report == null) return new Report();
    Report newReport = additionalCalcSupportComponent.makeCopyOfReport(report);
    newReport.setParent(null);
    reportRepository.save(newReport);
    List<ReportSample> reportSamples = reportSampleRepository.findByReportId(report);
    additionalCalculationService.createReportSamples(reportSamples, newReport);
    return newReport;
  }

  @NotNull
  private List<ReportToClosing> inactivateReportToClosing(List<Report> reportsByCase) {
    List<ReportToClosing> distinctReportToClosingList =
        reportToClosingRepository.findByReportIdList(
            reportsByCase.stream().map(Report::getId).collect(Collectors.toList()));
    distinctReportToClosingList.forEach(
        reportToClosing -> reportToClosing.setStatus(Boolean.FALSE));
    reportToClosingRepository.saveAll(distinctReportToClosingList);
    return distinctReportToClosingList;
  }

  /*use as an inverted method*/
  @Override
  public boolean isOnHoldOrCancelled(CaseStatus caseStatus) {
    return caseStatus == CaseStatus.CANCELED || caseStatus == CaseStatus.HOLD;
  }

  private void performPlateQc(Long caseId) {
    List<Plates> platesByCaseId = caseRepo.findPlateByCaseId(caseId);
    List<Samples> samplesFromCase =
        getSamplesFromCase(caseId).stream().map(CaseSampleMapping::getSamples).toList();
    List<Long> sampleIds =
        samplesFromCase.stream().map(Samples::getSampleId).collect(Collectors.toList());
    for (Plates plate : platesByCaseId) {
      int alertCount =
          qcValidationRepository.getPlateLevelAlertsBySamplesAndAcknowledgeStatus(
              sampleIds, Boolean.FALSE, ValidationStatus.ACTIVE, plate.getPlateId());
      if ((plate.getPlateProgressStatus() != PlateProgressStatus.PI_CALC_COMPLETED
              && plate.getPlateProgressStatus() != PlateProgressStatus.PI_CALC_REQUESTED)
          || (plate.getPlateProgressStatus() == PlateProgressStatus.PI_CALC_REQUESTED
              && alertCount > 0)) dataImportService.runPlateQC(plate.getPlateId(), null);
    }
  }

  /**
   * update case data api
   *
   * @param caseData
   * @param user
   * @return
   */
  public void updateCaseDetails(Cases caseData, UserMaster user) {
    List<AccessionCase> accessionCaseList =
        getAccessionCases(1L, List.of(caseData.getCaseNumber()), null, null);
    if (!CollectionUtils.isEmpty(accessionCaseList)) {
      AccessionCase caseDetails = accessionCaseList.get(0);
      try {
        log.info(objectMapper.writeValueAsString(caseDetails));
      } catch (Exception e) {
        log.error("debug log parse issue accession");
      }

      if (caseData.getCaseStatus() != CaseStatus.AWAITING_TESTING
          && caseData.getCaseStatus() != CaseStatus.NOT_READY
          && !isOnHoldOrCancelled(CaseStatus.getCaseStatus(caseDetails.getCaseStatus()))
          && piSupport.getCaseChangeAction() != null
          && !piSupport.getCaseChangeAction().equals(CASE_CHANGE_CASE_UPDATE)
          && !caseData.getIsNippTestType()
          && !caseData.getIsSnpTestType()) {
        caseData.setCaseStatus(CaseStatus.REVIEW_REQUIRED);
      }
      caseData.setDueDate(caseDetails.getOriginalDueDate());
      if (caseData.getEmboss() != null) {
        caseData.setEmboss(caseDetails.getEmboss());
      }
      if (caseData.getWetInk() != null) {
        caseData.setWetInk(caseDetails.getWetInk());
      }
      caseData.setCurrentDueDate(caseDetails.getDueDate());
      log.info(
          "[due date update] due date received for case number: {} - {}",
          caseData.getCaseNumber(),
          caseDetails.getDueDate());
      caseData.setAuditFlag(caseDetails.getAuditFlag());
      caseData.setDisplayFetusGender(
          caseDetails.getPrenatalDetailsResponse() != null
                  && caseDetails.getPrenatalDetailsResponse().getIsFetusGenderRequired() != null
              ? caseDetails.getPrenatalDetailsResponse().getIsFetusGenderRequired()
              : Boolean.FALSE);
      caseData.setCasePriority(
          caseDetails.getPriority() != null
              ? CasePriority.getCasePriority(caseDetails.getPriority())
              : CasePriority.ONE);
      caseData.setIsParent(caseDetails.getIsLinkedCase());
      caseData.setGroupId(caseDetails.getGroupId());
      caseData.setAgencyCaseNumber(caseDetails.getReference1());
      caseData.setDocketNo(caseDetails.getReference2());
      caseData.setLabNotes(caseDetails.getImmigNataComment());
      labVantageIntegrationService.saveSnpCaseDetails(caseDetails, caseData);
      labVantageIntegrationService.saveCaseCheckList(caseData);
      if (caseDetails.getChain() == 0) caseData.setCaseType(CaseType.NON_CHAIN);
      if (caseDetails.getChain() == 1) caseData.setCaseType(CaseType.CHAIN);
      TestTypeSubCategory testTypeSubCategory =
          labVantageIntegrationService.getTestTypeSubCategory(caseDetails.getSubCategory());
      caseData.setTestTypeSubCategory(testTypeSubCategory);
      List<Report> reportList = reportService.getReportsByParentIsNull(caseData.getCaseId());
      List<Integer> subTestTypes = new ArrayList<>();
      if (!CollectionUtils.isEmpty(
          PICalculationFactoryImpl.TEST_TYPE_SUB_MAP.get(
              testTypeSubCategory.getTestTypeSubCategory()))) {
        subTestTypes =
            new ArrayList<>(
                PICalculationFactoryImpl.TEST_TYPE_SUB_MAP.get(
                    testTypeSubCategory.getTestTypeSubCategory()));
      }

      for (Report report : reportList) {
        if (piSupport.getCaseChangeAction() != null
                && piSupport.isChangeCaseAction()
                && piSupport.getCaseChangeAction().equalsIgnoreCase(CASE_CHANGE_CASE_DEFINITION)
            || piSupport.getCaseChangeAction() != null
                && piSupport
                    .getCaseChangeAction()
                    .equalsIgnoreCase(CASE_DEF_UPDATE_WITH_STATUS_CHANGE)) {
          if (!CollectionUtils.isEmpty(subTestTypes)) {
            Integer selectedTestTypeId =
                subTestTypes.stream()
                    .filter(t -> Objects.equals(t, report.getTestTypeSubCategory().getId()))
                    .findFirst()
                    .orElse(subTestTypes.stream().findFirst().get());
            subTestTypes.removeIf(el -> Objects.equals(el, selectedTestTypeId));
            Optional<TestTypeSubCategory> subTestType =
                testTypeSubCategoryRepository.findById(selectedTestTypeId);
            testTypeSubCategory = subTestType.get();
          }
          report.setTestTypeSubCategory(testTypeSubCategory);
          TemplateMaster templateMaster = piHelper.setTemplateMaster(caseData, testTypeSubCategory);
          report.setReportTemplate(templateMaster);

          /*clears report calulations and report markers to unused,
          if the updated case def need new samples to reach ANR.*/
          clearReportDetailsIfSampleCountMismatchWithUpdatesCaseDef(
              caseData,
              report,
              caseDetails.getSamples(),
              caseSampleMappingRepository
                  .findByCaseDataCaseIdAndIsActive(caseData.getCaseId())
                  .stream()
                  .map(caseSample -> caseSample.getSamples())
                  .toList());
        }

        List<Integer> testTypeSubIdsImmutable =
            piCalculationFactory.getTestTypeSubIds(testTypeSubCategory.getTestTypeSubCategory());

        List<Integer> testTypeSubIds =
            CollectionUtils.isEmpty(testTypeSubIdsImmutable)
                ? new ArrayList<>()
                : new ArrayList<>() {
                  {
                    addAll(testTypeSubIdsImmutable);
                  }
                };
        Optional<Integer> existingReportId =
            testTypeSubIds.stream()
                .filter(id -> report.getTestTypeSubCategory().getId() == id)
                .findFirst();
        if (!CollectionUtils.isEmpty(testTypeSubIds)
            && testTypeSubIds.contains(report.getTestTypeSubCategory().getId())) {
          testTypeSubIds.removeIf(id -> report.getTestTypeSubCategory().getId() == id);
        }
        if (existingReportId.isPresent()) {
          existingReportId.ifPresent(
              testTypeId -> {
                Optional<TestTypeSubCategory> testTypeOptional =
                    testTypeSubCategoryRepository.findById(testTypeId);
                testTypeOptional.ifPresent(report::setTestTypeSubCategory);
              });
        }
      }

      reportList = reportRepository.saveAll(reportList);
      /*      Optional<Agency> agencyOptional =
          labVantageIntegrationService.getAgency(caseDetails.getAccountId());
      agencyOptional.ifPresent(caseData::setAgency);*/
      Agency agency = getAgency(caseDetails);
      caseData.setAgency(agency);
      createDDCChannelMaster(caseData, caseDetails);
      caseRepo.save(caseData);
      updateCaseReportLanguages(caseDetails, caseData);
      labVantageIntegrationService.saveCaseIndicator(
          caseDetails.getIndicatorResponse().stream()
              .filter(indicator -> indicator.getIndicatorId() != null)
              .collect(Collectors.toList()),
          caseData,
          caseDetails.getSamples(),
          caseDetails.getAccountId());
      List<AssertionSampleResponse> sampleDetails = caseDetails.getSamples();
      List<CaseSampleMapping> caseSampleMappings =
          caseSampleMappingRepository.findByCaseDataCaseId(caseData.getCaseId());
      Map<String, AssertionSampleResponse> sampleNumberSampleResponseMap =
          sampleDetails.stream()
              .collect(
                  Collectors.toMap(
                      AssertionSampleResponse::getSampleNumber,
                      Function.identity(),
                      (existing, replacement) -> replacement));
      caseSampleMappings.forEach(
          caseSample -> {
            if (!sampleNumberSampleResponseMap.containsKey(
                caseSample.getSamples().getSampleCode())) {
              caseSample.setActive(false);
            } else {
              AssertionSampleResponse sampleResponse =
                  sampleNumberSampleResponseMap.get(caseSample.getSamples().getSampleCode());
              if (StringUtils.isNotBlank(sampleResponse.getTestPartyRole())) {
                Optional<TestPartyRole> optionalTestPartyRole =
                    testPartyRoleRepository.findByRole(sampleResponse.getTestPartyRole());
                optionalTestPartyRole.ifPresent(caseSample::setTestPartyRole);
              }
              boolean viabilityCheckRequiredFromANC =
                  labVantageIntegrationService.isViabilityCheckRequiredFromANC(
                      sampleResponse, caseDetails.getCaseStatus());
              caseSample.setViabilityCheckRequired(viabilityCheckRequiredFromANC);
              //              if(StringUtils.isNotBlank(sampleResponse.getRace())) {
              //                TestPartyRace testPartyRace =
              // testPartyRaceRepository.findByRace(sampleResponse.getRace()).orElse(null);
              Samples sample = caseSample.getSamples();
              sample.setRace(sampleResponse.getRace());
              sampleRepository.save(sample);
              //              }
            }
          });
      caseSampleMappingRepository.saveAll(caseSampleMappings);
      sampleDetails.forEach(
          sampleResponse -> {
            if (sampleResponse.getSampleNumber() != null) {
              labVantageIntegrationService.createSample(
                  sampleResponse, caseDetails.getChain() == 0);
            }
          });
    } else {
      throw new BadRequestException(
          MessageFormat.format(errorMessage.getCaseNotFound(), caseData.getCaseNumber()));
    }
  }

  /**
   * @param caseData - case data
   * @param report - report
   * @param sampleListANC - sample response from ANC
   * @param sampleListANR - samples in ANR. clears reports details and report markers will be moved
   *     to unused, if there is a mis-match between active samples for the new case definition in
   *     ANC and ANR.
   */
  private void clearReportDetailsIfSampleCountMismatchWithUpdatesCaseDef(
      Cases caseData,
      Report report,
      List<AssertionSampleResponse> sampleListANC,
      List<Samples> sampleListANR) {
    if (sampleListANC.size() > sampleListANR.size()
        || sampleListANC.size() < sampleListANR.size()) {
      List<ReportMarker> reportMarkerList =
          reportMarkerRepository.findByReportIdAndIsUsed(report.getId(), Boolean.TRUE);
      reportMarkerList.forEach(rm -> rm.setIsUsed(false));
      reportService.resetReportCalculation(caseData.getCaseId(), report);
      reportMarkerRepository.saveAll(reportMarkerList);
    }
  }

  @Override
  public Agency getAgency(AccessionCase caseDetails) {
    ResponseEntity<SchedulingAgencyResponse> agencyDetails = null;
    Agency agency = new Agency();
    try {
      agencyDetails = getAgencyDetails(caseDetails.getAccountId());
    } catch (Exception e) {
      log.error(e.getMessage());
    }

    if (agencyDetails != null && agencyDetails.getBody() != null) {
      SchedulingAgencyData data = agencyDetails.getBody().getData();
      agency.setAgencyName(data.getAccountDetails().getAccountName());
      agency.setAgencyLogo(data.getAccountDetails().getLogoUrl());
      agency.setAgencyId(caseDetails.getAccountId());
      agency.setStatus(1);
      return agencyRepository.save(agency);
    }
    return null;
  }

  private void createDDCChannelMaster(Cases caseData, AccessionCase caseDetails) {
    DdcChannelsMaster ddcChannelsMaster =
        labVantageIntegrationService.getDdcChannelsMaster(caseDetails.getChannelId());
    caseData.setDdcChannelsMaster(ddcChannelsMaster);
    caseData.setAuthenticationCode(caseDetails.getAuthenticationCode());
    String docketNumber = caseData.getDocketNo();
    if (StringUtils.isEmpty(docketNumber)) {
      docketNumber = caseDetails.getReference2();
    }
    caseData.setDocketNo(docketNumber);
    String agencyCaseNumber = caseData.getAgencyCaseNumber();
    if (StringUtils.isEmpty(agencyCaseNumber)) {
      agencyCaseNumber = caseDetails.getReference1();
    }
    caseData.setAgencyCaseNumber(agencyCaseNumber);
  }

  private void updateCaseReportLanguages(AccessionCase caseDetails, Cases caseData) {
    if (!caseDetails.getCaseReportLanguage().isEmpty()) {
      List<CaseReportLangugae> byCaseId =
          caseReportLanguageRepository.findCaseReportLanguageByCaseId(caseData.getCaseId());
      if (!byCaseId.isEmpty()) {
        caseReportLanguageRepository.deleteAll(byCaseId);
      }
      List<AccessioningCaseReportLanguageResponse> caseReportLanguage =
          caseDetails.getCaseReportLanguage();
      labVantageIntegrationService.saveCaseReportLanguages(caseReportLanguage, caseData);
    }
  }

  @Override
  public void saveCaseChangeRequest(
      Cases caseData, CaseChangeActionRequest caseChangeActionRequest) {
    CaseChangeRequest caseChangeRequest = new CaseChangeRequest();
    caseChangeRequest.setCaseChangeRequestStatus(1);
    caseChangeRequest.setRequestDueToChange(caseChangeActionRequest.getChangeAction());
    caseChangeRequest.setCaseData(caseData);
    caseChangeRequest.setCreatedDate(new Date());
    caseChangeRequest.setChangeComment(caseChangeActionRequest.getChangeComment());
    /*flush added since there are other processes in the same flow that needs this updated data
     especially qc check for case definition change alerts that depends on this log*/
    caseChangeRequestRepository.saveAndFlush(caseChangeRequest);
  }

  @Override
  public List<CaseIndicatorMapping> getIndicatorsOfCaseIdAndSlug(Long caseId, String slug) {
    return caseIndicatorMappingRepository.findByCaseIdCaseIdAndIndicatorIdIndicatorSlug(
        caseId, slug);
  }

  @Override
  public List<Samples> getViableSamplesFromCase(Cases caseData) {
    return caseSampleMappingRepository.findBySamplesByViabilityIsTrueAndCaseId(
        caseData.getCaseId());
  }

  @Override
  public CaseSampleMapping getCaseSampleMapping(Cases aCase, Samples sample) {
    Object[] errorParams = {aCase.getCaseNumber(), sample.getSampleCode()};
    return caseSampleMappingRepository
        .findByCaseDataAndSamples(aCase, sample)
        .orElseThrow(
            () ->
                new NotFoundException(
                    MessageFormat.format(
                        errorMessage.getCaseSampleMappingNotFound(), errorParams)));
  }

  @Override
  public List<CaseSampleMapping> getCaseSampleMappingByPlateAndSampleAndViable(
      Samples sample, Plates plate, boolean viableStatus) {
    return caseSampleMappingRepository.findByCaseSampleMappingBySampleAndPlateAndViableStatus(
        sample, plate, viableStatus);
  }

  @Override
  public List<CaseNumberAndCaseIdProjection> getCaseNumberAndCaseIdBySampleAndViabilityCheckIsTrue(
      Samples sample) {
    return caseSampleMappingRepository.getCaseNumberAndCaseIdBySampleAndViabilityCheckIsTrueAnd(
        sample);
  }

  private void validateCaseForClosing(
      List<CaseApproval> caseApprovals,
      TestTypeSubCategory testTypeSubCategory,
      Long caseId,
      UserMaster user) {
    TestTypeSettingResponse testTypeSettings =
        testTypeService.getTestTypeSettings(testTypeSubCategory);
    caseApprovals.stream()
        .filter(cApprovals -> cApprovals.getReport().isActive())
        .forEach(
            caseApproval -> {
              if (Boolean.TRUE.equals(
                  !caseApproval.getIsPICApproved() && !testTypeSettings.isBlockCombinedIndexArea()))
                throw new BadRequestException(errorMessage.getCaseNotQualify());
              if (Boolean.TRUE.equals(!caseApproval.getIsReportApproved()))
                throw new BadRequestException(errorMessage.getCaseNotQualify());
              if (Boolean.TRUE.equals(!caseApproval.getIsResultApproved()))
                throw new BadRequestException(errorMessage.getCaseNotQualify());
              if (Boolean.TRUE.equals(!caseApproval.getIsInterpretationApproved())
                  && !testTypeSettings.isBlockInterpretationArea())
                throw new BadRequestException(errorMessage.getCaseNotQualify());
            });
  }

  /**
   * checks the case status on anc to confirm that the API call for updating the case status to
   * analysis in progress was a success.
   *
   * @param caseId caseId is used to fetch case details and the corresponding caseNumber to use as
   *     parameter to feign call
   * @param user to generate token for feign call
   * @return Boolean of whether the case status is actually analysis in progress on anc system
   */
  public Boolean checkForCaseStatus(Long caseId, UserMaster user) {
    String auth = azureTokenUtils.getAzureTokenOfOutBoundAPI(user);
    Cases aCase = getCase(caseId);
    ResponseEntity<AccessioningBasicResponse> accessionResponse =
        accessioningFeignClient.getCaseDetails(auth, Long.valueOf(aCase.getCaseNumber()));

    if (accessionResponse.getBody() != null) {
      AccessionCaseResponse caseDetails =
          Optional.ofNullable(accessionResponse.getBody().getData())
              .map(AssertionData::getCaseDetails)
              .orElseThrow(() -> new BadRequestException(errorMessage.getMissingCaseDetails()));
      return Objects.equals(caseDetails.getCaseStatus(), ANC_ANALYSIS_CASE_STATUS_RESPONSE);
    }
    throw new BadRequestException(errorMessage.getUnableToFetchCaseDetails());
  }

  /**
   * checks in fault tolerance if there are any pending api calls in retry by checking the
   * transaction status on the fault tolerance data
   *
   * @param caseId for fetching corresponding fault tolerance details
   * @return Boolean of whether exists failed api calls of case status
   */
  public Boolean failedApiStatusCalls(Long caseId) {

    return integrationFaultToleranceRepository
        .existsByEntityIdAndIntegrationApiMasterIdAndTransactionStatus(
            caseId, ANC_CASE_STATUS.getApiId(), 0);
  }

  @Override
  public void assignToDataAnalyst(Cases aCase, UserMaster user) {
    aCase.setCaseAssignee(null);
    aCase.setCaseProgressStatus(UN_ASSIGNED);
    aCase.setCaseStatus(CaseStatus.REVIEW_REQUIRED);
    saveCaseHistory(aCase.getCaseId(), user, UN_ASSIGNED.getStatus());
    caseRepo.save(aCase);
  }

  private boolean isAllReportResultInclusion(Cases aCase) {
    List<Report> reports = reportService.findReportByCaseAndParentIsNull(aCase);
    return reports.stream()
        .allMatch(
            el ->
                el.getReportResult() != null && el.getReportResult().getId().equals(INCLUSION_ID));
  }

  @Override
  public boolean hasIndicatorToPhdReview(Cases aCase) {
    List<CaseIndicatorMapping> caseIndicators = caseIndicatorMappingRepository.findByCaseId(aCase);
    List<String> phdIndicators =
        List.of(
            TemplateConstants.SUMMARY_REQ_SLUG,
            TemplateConstants.AFFIDAVIT_SLUG,
            TemplateConstants.NY_SLUG,
            TemplateConstants.ALL_RDO_SLUG,
            TemplateConstants.ONE_SAMPLE_BOTH_CASE_SLUG,
            TemplateConstants.SURROGACY_SLUG);
    boolean hasNataIndicator =
        (caseIndicators != null
            && caseIndicators.stream()
                .anyMatch(
                    el ->
                        el.getIndicatorId() != null
                            && List.of(TemplateConstants.NATA_SLUG, TemplateConstants.SCC_SLUG)
                                .contains(el.getIndicatorId().getIndicatorSlug())));
    return isAllReportResultInclusion(aCase)
        && (CollectionUtils.isEmpty(caseIndicators)
            || (caseIndicators != null
                && caseIndicators.stream()
                    .anyMatch(
                        el ->
                            phdIndicators.contains(
                                el.getIndicatorId() != null
                                    ? el.getIndicatorId().getIndicatorSlug()
                                    : null))
                && !hasNataIndicator));
  }

  @Override
  public boolean hasIndicatorToSupervisorReview(Cases aCase) {
    List<CaseIndicatorMapping> caseIndicators = caseIndicatorMappingRepository.findByCaseId(aCase);

    List<String> supervisorIndicators =
        List.of(TemplateConstants.RESTRICTED_SLUG, TemplateConstants.CASE_FLAGGED_SLUG);

    return isAllReportResultInclusion(aCase)
        && (caseIndicators.stream()
                .anyMatch(
                    el ->
                        supervisorIndicators.contains(
                            el.getIndicatorId() != null
                                ? el.getIndicatorId().getIndicatorSlug()
                                : null))
            || aCase.getDdcChannelsMaster().getId().equals(TemplateConstants.MEDIA_ID));
  }

  @Override
  public void updateCaseLabNotes(
      Cases aCase, CaseLabNotesRequest caseLabNotesRequest, UserMaster user) {
    aCase.setModifiedUser(user);
    aCase.setModifiedDate(Instant.now());
    aCase.setLabNotes(caseLabNotesRequest.getLabNote());
    caseRepo.save(aCase);
  }

  @Override
  public List<MarkerPIProjection> getActiveLociOfCase(Long caseId, Long reportId) {
    Sort sort = Sort.by(Sort.Order.by(MarkerMaster_.SORT_ORDER));
    return sampleMarkerRepository.findMarkerAndPiByCaseIdAndIsUsed(
        caseId, Boolean.TRUE, reportId, sort);
  }

  @Override
  public List<Samples> findSamplesByCaseAndViableStatus(Cases aCase, boolean viableStatus) {
    return caseSampleMappingRepository.findBySamplesByViabilityCheckIsTrueAndCaseIdAndViableStatus(
        aCase.getCaseId(), viableStatus);
  }

  @Override
  public CaseCommentPageResponse getAncCaseComments(
      String caseNumber, Integer reqType, Integer pageNo, Integer pageSize) {
    String auth = azureTokenUtils.getAzureTokenOfOutBoundAPI(null);
    Long caseNumberLong = Long.parseLong(caseNumber);
    ResponseEntity<CaseCommentBaseResponse> caseComments =
        accessioningCaseCommentFeignClient.getCaseComments(
            auth, caseNumberLong, reqType, pageNo, pageSize);
    CaseCommentPageResponse caseCommentPageResponse;
    caseCommentPageResponse = Objects.requireNonNull(caseComments.getBody()).getData();
    return caseCommentPageResponse;
  }

  @Override
  public Cases getCaseDataByCaseNumber(String caseNumber) {
    return getCaseByCaseNumber(caseNumber)
        .orElseThrow(
            () -> new NotFoundException(format(errorMessage.getCaseNumberNotFound(), caseNumber)));
  }

  @Override
  public List<AccessionCase> getAccessionCases(
      Long count, List<String> caseNumberList, Long faultToleranceId, Long caseNumber) {
    String auth = azureTokenUtils.getAzureTokenOfOutBoundAPI(null);
    UserMaster user = null;
    String endPoint = accessioningBaseURL + caseListURL;
    String advSchReq =
        "{\"caseIdOrGroupIds\":"
            + "\""
            + caseNumberList.stream().map(Object::toString).collect(Collectors.joining(","))
            + "\"}";
    String requestPayload = null;
    try {
      requestPayload = objectMapper.writeValueAsString(caseNumberList);
    } catch (JsonProcessingException e) {
      log.error("case number list in bulk save " + e.getMessage());
    }
    ActivityLog activityLog =
        activityLogService.createActivityLog(
            new Throwable().getStackTrace()[0], user, requestPayload, endPoint);

    ResponseEntity<CaseListResponse> assertionBasicResponseEntity =
        accessioningFeignClient.getCaseList(
            auth, advSchReq, String.valueOf(caseNumberList.size()), "as", "1");
    CaseListResponse accessioningBasicResponse = assertionBasicResponseEntity.getBody();
    if (accessioningBasicResponse == null || accessioningBasicResponse.getData() == null)
      throw new BadRequestException(
          MessageFormat.format(errorMessage.getCaseNotFound(), caseNumberList.toString()));
    activityLogService.updateActivityLog(activityLog, assertionBasicResponseEntity);
    return accessioningBasicResponse.getData().getCases();
  }

  @Override
  public List<String> getLinkedCaseNumbersFromAccessioning(Long groupId) {
    if (groupId == null) return List.of();
    String auth = azureTokenUtils.getAzureTokenOfOutBoundAPI(null);
    ResponseEntity<CaseListResponse> assertionBasicResponseEntity =
        accessioningFeignClient.getLinkedCases(groupId.toString(), auth);
    CaseListResponse accessioningBasicResponse = assertionBasicResponseEntity.getBody();
    if (accessioningBasicResponse == null || accessioningBasicResponse.getData() == null)
      throw new BadRequestException(MessageFormat.format(errorMessage.getCaseNotFound(), groupId));
    return accessioningBasicResponse.getData().getCases().stream()
        .map(cases -> cases.getCaseId().toString())
        .toList();
  }

  /**
   * @param caseNumberList - case number payload. Using the case number list provided calls ANC
   *     minimal case response API to fetch and store distinct groupIds of all cases in the case
   *     number list, then another API call is initiated to fetch all the group case details of the
   *     groupIds and returns the list of all caseNumbers
   * @return case numbers of all linked cases in the payload received
   * @throws ExternalServiceException in case the response values of the API calls are null or
   *     empty.
   */
  @Override
  public List<Long> fetchAllLinkedCasesOfCaseNumberList(List<String> caseNumberList) {
    Set<Long> groupIds = null;
    try {
      groupIds =
          Objects.requireNonNull(
                  accessioningFeignClient
                      .getCaseMinimalList(
                          caseNumberList.stream()
                              .map(Object::toString)
                              .collect(Collectors.joining(",")))
                      .getBody())
              .getData()
              .getCaseResponses()
              .stream()
              .map(AccessionCase::getGroupId)
              .collect(Collectors.toSet());
    } catch (NullPointerException N) {
      throw new ExternalServiceException(
          "Something went wrong while fetching minimal-case-detail API.");
    }

    String groupIdSearchRequest =
        groupIds.stream().map(Object::toString).collect(Collectors.joining(","));
    String auth = azureTokenUtils.getAzureTokenOfOutBoundAPI(null);
    ResponseEntity<CaseListResponse> assertionBasicResponseEntity =
        accessioningFeignClient.getLinkedCases(groupIdSearchRequest, auth);
    CaseListResponse accessioningBasicResponse = assertionBasicResponseEntity.getBody();
    if (accessioningBasicResponse == null || accessioningBasicResponse.getData() == null)
      throw new ExternalServiceException("Something went wrong while fetching case-detail API.");
    return accessioningBasicResponse.getData().getCases().stream()
        .map(AccessionCase::getCaseId)
        .toList();
  }

  @Override
  public void deleteFromCaseReviewLog(Cases caseData, String caseReviewType, UserMaster user) {
    if (caseData.getCaseStatus() == CaseStatus.MOVED_TO_CLOSE
        || caseData.getCaseStatus() == CaseStatus.CASE_CLOSED) return;
    Optional<CaseReviewLog> reviewLog = getCaseReviewLog(caseData, caseReviewType);
    reviewLog.ifPresent(
        review -> {
          review.setIsActive(Boolean.FALSE);
          caseReviewLogRepository.save(review);
        });
  }

  @Override
  public void updateCaseDetailsFromANC(Long caseNumber) {
    Optional<Cases> optionalCase = getCaseByCaseNumber(caseNumber.toString());
    if (optionalCase.isEmpty())
      throw new BadRequestException(
          MessageFormat.format(errorMessage.getCaseNotFound(), caseNumber));
    Cases aCase = optionalCase.get();
    List<AccessionCase> accessionCases =
        Collections.unmodifiableList(
            getAccessionCases(
                1L, List.of(caseNumber.toString()), null, Long.valueOf(aCase.getCaseNumber())));
    AccessionCase caseDetail = accessionCases.get(0);
    // TODO: no need to throw exception
    if (caseDetail.getCaseStatus() == null
        || CaseStatus.getCaseStatus(caseDetail.getCaseStatus()) == null)
      throw new BadRequestException(errorMessage.getCaseStatusNotFound());
    aCase.setCaseStatus(CaseStatus.getCaseStatus(caseDetail.getCaseStatus()));
    caseRepo.save(aCase);
  }

  /*fetches the case list from anc and updates cancelled cases*/
  @Override
  public void updateCaseStatusFromAnc(List<String> caseNumber) {
    try {
      log.info("[cancelled_status] Processing case numbers: {}", caseNumber);
      List<AccessionCase> accessionCases =
          Collections.unmodifiableList(
              getAccessionCases(
                  (long) caseNumber.size(), caseNumber, null, Long.valueOf(caseNumber.get(0))));
      accessionCases.forEach(
          accessionCase -> {
            Optional<Cases> aCase = getCaseByCaseNumber(accessionCase.getCaseId().toString());
            if (accessionCase.getCaseStatus().equals(CaseStatus.CANCELED.getStatus())
                || accessionCase.getCaseStatus().equals(CaseStatus.HOLD.getStatus())) {
              aCase.ifPresent(
                  cases ->
                      cases.setCaseStatus(CaseStatus.getCaseStatus(accessionCase.getCaseStatus())));
            }
          });
    } catch (Exception e) {
      log.error(
          "[cancelled_status] failed to update case status for cases: {}. Exception: {}",
          caseNumber,
          e.getMessage(),
          e);
    }
  }

  @Override
  public boolean hasNataOrrSccIndicator(Cases aCase) {
    return !findNataAndSccIndicatorsOfCase(aCase.getCaseId()).isEmpty();
  }

  /*case approvals are fetched against the case and filtered by taking max of the last modified case approval*/
  @Override
  public void updateCaseApprovals(Cases aCase) {
    List<CaseApproval> caseApprovalByCaseId =
        caseApprovalRepository.findCaseApprovalByCaseId(aCase);
    Optional<CaseApproval> optionalCaseApproval =
        caseApprovalByCaseId.stream()
            .max(Comparator.comparing(AbstractAuditingEntity::getModifiedDate));
    if (optionalCaseApproval.isEmpty()) return;
    CaseApproval caseApproval = optionalCaseApproval.orElseGet(CaseApproval::new);
    caseApproval.setIsResultApproved(false);
    caseApproval.setIsReportApproved(false);
    caseApproval.setIsPICApproved(false);
    caseApproval.setIsInterpretationApproved(false);
    caseApproval.setModifiedUser(requestSession.getUser());
    caseApprovalRepository.save(caseApproval);
  }

  @Override
  @Transactional
  public void tagCase(Long caseId, Long userId, Integer tagAction) {
    switch (TagAction.getTagActionName(tagAction)) {
      case TAG -> addTag(caseId, userId);
      case REMOVE_TAG -> deleteTag(caseId, userId);
      case REMOVE_ALL_TAGS -> deleteAllTagExceptCurrentUser(caseId, userId);
    }
  }

  /**
   * if the user role is supervisor removes all existing tags against that case except the one the
   * current user has.
   *
   * @param caseId - for case object
   * @param userId - to verify user role
   */
  private void deleteAllTagExceptCurrentUser(Long caseId, Long userId) {
    List<CaseTagMapping> byCaseId =
        caseTagMappingRepository.findByCaseId(caseRepo.getByCaseId(caseId));
    if (checkForSupervisorRole(userId)) {
      caseTagMappingRepository.deleteAllById(
          byCaseId.stream()
              .filter(tag -> !Objects.equals(tag.getUserId(), userId))
              .map(CaseTagMapping::getId)
              .toList());
    }
  }

  private boolean checkForSupervisorRole(Long userId) {
    return userService.doesUserHaveRolesIn(
        userService.getUser(userId), List.of(PHD_SUPERVISOR_ROLE, SUPERVISOR_ROLE));
  }

  private void deleteTag(Long caseId, Long userId) {
    caseTagMappingRepository.deleteByCaseIdAndUserId(getCase(caseId), userId);
  }

  private void addTag(Long caseId, Long userId) {
    CaseTagMapping caseTagMapping =
        caseTagMappingRepository.findByCaseIdAndUserId(getCase(caseId), userId);
    caseTagMapping = caseTagMapping != null ? caseTagMapping : new CaseTagMapping();
    caseTagMapping.setCaseId(getCase(caseId));
    caseTagMapping.setUserId(userId);
    caseTagMappingRepository.save(caseTagMapping);
  }

  @Override
  public boolean getTag(Cases aCase, Long userId) {
    return caseTagMappingRepository.existsByCaseIdAndUserId(aCase, userId);
  }

  @Override
  public List<String> getTaggedUserOfCase(Cases aCase) {
    List<CaseTagMapping> caseTagMappingList = caseTagMappingRepository.findByCaseId(aCase);
    List<String> caseTagResponses = new ArrayList<>();
    caseTagMappingList.forEach(
        caseTagMapping ->
            caseTagResponses.add(userService.getUser(caseTagMapping.getUserId()).getName()));
    return caseTagResponses;
  }

  /*To get all cases linked to the given case id*/
  @Override
  public List<Cases> getAllLinkedCasesInAnr(Long caseId, Boolean includeClosedCases) {
    Cases caseData = getCase(caseId);
    if (caseData.getIsParent()) {
      List<Cases> linkedCasesList = caseRepo.findByGroupId(caseData.getGroupId());
      linkedCasesList.removeIf(cases -> isClosed(cases.getCaseStatus()) && !includeClosedCases);
      return linkedCasesList;
    }
    return List.of(caseData);
  }

  @Override
  public List<String> getAllLinkedCaseNumbers(Long groupId) {
    return caseRepo.findCaseNumberByGroupId(groupId);
  }

  @Override
  public void withdrawLinkedCasesToPHDPrimaryReview(Long caseId, UserMaster user) {
    getAllLinkedCasesInAnr(caseId, true)
        .forEach(
            caseData -> {
              if (caseData.getCaseStatus() == CaseStatus.MOVED_TO_CLOSE) {
                withdrawToPHDPrimaryReview(caseData, user);
                updateSignOutDate(user, caseData, null, null);
              }
            });
  }

  public boolean checkIfCaseStatusWasUpdatedForGroupStatus(Cases aCase) {
    if (aCase.getCaseStatus() == CaseStatus.AWAITING_TESTING) {
      return piHelper.getActiveSamplesWithoutReprocess(
          aCase.getCaseSampleMapping().stream()
              .map(CaseSampleMapping::getSamples)
              .collect(Collectors.toList()),
          aCase.getCaseId());
    }
    return false;
  }

  @Override
  public void updateCaseGroupStatus(
      Cases aCase, boolean isStatusOverride, UserMaster user, String caseChangeAction) {
    try {
      Thread.sleep(1000);
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
    List<Cases> linkedCases =
        getAllLinkedCasesInAnr(
            aCase.getCaseId(),
            !Boolean.TRUE.equals(
                caseChangeAction != null
                    ? caseChangeAction.equals(CASE_CHANGE_AMEND)
                    : Boolean.TRUE));
    // Due to some unknown transaction issue we didn't get the actual case status here,
    // so we did it like this for temp fix.
    for (var cases : linkedCases) {
      String query = "SELECT c.caseStatus FROM Cases c WHERE caseId =" + cases.getCaseId();
      String caseStatusOrdinal = jdbcTemplate.queryForObject(query, String.class);
      CaseStatus caseStatus = CaseStatus.fromOrdinal(Integer.parseInt(caseStatusOrdinal));
      cases.setCaseStatus(caseStatus);
    }
    if (linkedCases.size() > 1) {
      boolean newCaseFound =
          linkedCases.stream().anyMatch(cases -> cases.getCaseStatus() == CaseStatus.NOT_READY);
      if (newCaseFound) {
        aCase.setOriginalCaseStatus(aCase.getCaseStatus());
        aCase.setCaseStatus(CaseStatus.AWAITING_TESTING);
        caseRepo.save(aCase);
        return;
      }

      /*this will manage groupStatus while overriding case status of related cases*/
      checkForCaseStatusOverride(aCase, isStatusOverride, linkedCases);

      /*considering the scenario where after syncing cases status on linked cases, if a rerun plate is bought up with one of the linked case
       * this condition make sure that the original case status will be updated only if the current status is higher than the original cases status*/
      linkedCases.forEach(
          lCase -> {
            if (lCase.getOriginalCaseStatus() == null
                || lCase.getCaseStatus().getIndex() > lCase.getOriginalCaseStatus().getIndex())
              lCase.setOriginalCaseStatus(lCase.getCaseStatus());
          });
      Optional<Integer> minCaseStatusIndex = Optional.empty();
      if (StringUtils.isNotBlank(caseChangeAction)) {
        minCaseStatusIndex =
            linkedCases.stream()
                .map(el -> el.getOriginalCaseStatus().getIndex())
                .min(Integer::compareTo);
      } else {
        minCaseStatusIndex =
            linkedCases.stream().map(el -> el.getCaseStatus().getIndex()).min(Integer::compareTo);
      }

      Integer minIndex = minCaseStatusIndex.get();
      CaseStatus lowestCaseStatus = CaseStatus.getCaseStatus(minIndex);
      for (Cases linkedCase : linkedCases) {
        if (lowestCaseStatus != null && linkedCase.getCaseStatus() != lowestCaseStatus) {
          saveCaseHistory(linkedCase.getCaseId(), user, lowestCaseStatus.getStatus());
        }
        linkedCase.setCaseStatus(lowestCaseStatus);
      }
      caseRepo.saveAll(linkedCases);
    }
  }

  private void checkForCaseStatusOverride(
      Cases aCase, boolean isStatusOverride, List<Cases> linkedCases) {
    if (isStatusOverride) {
      for (Cases cases : linkedCases) {
        if (!checkIfCaseStatusWasUpdatedForGroupStatus(cases) && !cases.equals(aCase)) {
          cases.setCaseStatus(cases.getOriginalCaseStatus());
        }
      }
      caseRepo.saveAll(linkedCases);
    }
  }

  @Override
  public void syncLinkedCaseStatus(Cases aCase, UserMaster user) {
    List<Cases> linkedCases = getAllLinkedCasesInAnr(aCase.getCaseId(), false);
    List<CaseQCRequest> caseQCRequests = new ArrayList<>();
    if (linkedCases != null && linkedCases.size() > 1) {
      linkedCases.forEach(
          cases -> {
            CaseQCRequest caseQCRequest = new CaseQCRequest();
            caseQCRequest.setCaseId(cases.getCaseId());
            caseQCRequest.setTestTypeId(cases.getTestTypeSubCategory().getId());
            caseQCRequests.add(caseQCRequest);
          });
      caseQCRequests.forEach(caseQCRequest -> caseQCFactory.computeCaseQC(caseQCRequest, user));
    } else {
      CaseQCRequest caseQCRequest = new CaseQCRequest();
      caseQCRequest.setCaseId(aCase.getCaseId());
      caseQCRequest.setTestTypeId(aCase.getTestTypeSubCategory().getId());
      caseQCFactory.computeCaseQC(caseQCRequest, user);
    }
    boolean containsReprocessRequestedCase =
        linkedCases.stream()
            .anyMatch(cases -> cases.getCaseStatus() == CaseStatus.AWAITING_TESTING);
    if (containsReprocessRequestedCase) {
      linkedCases.forEach(cases -> cases.setCaseStatus(CaseStatus.AWAITING_TESTING));
    }
  }

  @Override
  public boolean isLinkedCase(Cases aCase) {
    return aCase.getIsParent();
  }

  @Override
  public boolean isCaseClosed(Cases aCase) {
    return aCase.getCaseStatus() == CaseStatus.CASE_CLOSED
        || aCase.getCaseStatus() == CaseStatus.MOVED_TO_CLOSE;
  }

  /*   Synchronizes help ticket updates for linked cases.
  updates to prevent deadlocks,
  * - Individual case processing (fetch, update, save)
  * - Always using latest data state
  * - Immediate saves after updates*/
  @Transactional
  @Override
  public void syncHelpTicketUpdates(Cases aCase, List<Integer> action) {
    List<Long> linkedCaseIds =
        getAllLinkedCasesInAnr(aCase.getCaseId(), false).stream()
            .map(cases -> cases.getCaseId())
            .toList();

    for (Long caseId : linkedCaseIds) {
      Cases cases =
          caseRepo
              .findById(caseId)
              .orElseThrow(() -> new NotFoundException("Case not found: " + caseId));

      for (Integer actionId : action) {
        updateCaseBasedOnAction(cases, actionId);
      }

      caseRepo.save(cases);
    }
  }

  private void updateCaseBasedOnAction(Cases cases, Integer actionId) {
    switch (SyncAction.getSyncActionName(actionId)) {
      case ADD -> cases.setInHelpFolder(true);
      case REMOVE -> cases.setInHelpFolder(false);
      case ACTIVE -> cases.setHelpTicketStatus(true);
      case INACTIVE -> cases.setHelpTicketStatus(false);
      case HOLD_AT_ANALYSIS -> cases.setHoldAtDepartment(true);
      case HOLD_AT_REPORTING -> cases.setHoldAtDepartmentReporting(true);
      case RELEASE_FROM_DEPARTMENT -> cases.setHoldAtDepartment(false);
      case RELEASE_FROM_REPORTING -> cases.setHoldAtDepartmentReporting(false);
      default -> throw new BadRequestException("Missing/Unknown action value");
    }
  }

  /**
   * calling this function will log all delay case requests, created and modified user will be the
   * same since there is no update on existing data once created.
   *
   * @param caseList used to obtain current and original due date
   * @param caseDueDate update due date to log
   * @param user to record user against the due date change action
   */
  @Override
  public void logDelayCaseHistory(List<Cases> caseList, Date caseDueDate, UserMaster user) {
    List<DelayCaseHistory> delayCaseHistoryList = new ArrayList<>();
    caseList.forEach(
        aCase -> {
          DelayCaseHistory delayCaseHistory =
              DelayCaseHistory.builder()
                  .caseId(aCase)
                  .currentDueDate(aCase.getCurrentDueDate())
                  .originalDueDate(aCase.getDueDate())
                  .updatedDueDate(caseDueDate)
                  .build();
          delayCaseHistory.setModifiedUser(user);
          delayCaseHistory.setCreatedUser(user);
          delayCaseHistoryList.add(delayCaseHistory);
        });
    delayCaseHistoryRepository.saveAll(delayCaseHistoryList);
  }

  /**
   * this function filters the delay case history using the filter string provided using jpa
   * specification.
   *
   * @param filter encoded filter search request
   * @return DelayCaseHistoryPageResponse which contains page info and DelayCaseHistoryResponse
   * @throws BadRequestException is thrown if the filter request is invalid
   */
  @Override
  public DelayCaseHistoryPageResponse findAllDelayCaseHistory(String filter) {
    String decodedFilter = URLDecoder.decode(filter, StandardCharsets.UTF_8);
    Page<DelayCaseHistory> delayCaseHistoryPage;
    try {
      DelayCaseHistorySearchRequest delayCaseHistorySearchRequest =
          objectMapper.readValue(decodedFilter, DelayCaseHistorySearchRequest.class);
      int pageNumber = delayCaseHistorySearchRequest.getPageNumber();
      // Frontend starts page from 1
      pageNumber = pageNumber - 1;
      Pageable pageable = PageRequest.of(pageNumber, delayCaseHistorySearchRequest.getPageSize());
      delayCaseHistoryPage =
          delayCaseHistoryRepository.findAll(
              DelayCaseHistorySpecification.getDelayCaseHistory(
                  delayCaseHistorySearchRequest, entityManager),
              pageable);
    } catch (JsonProcessingException e) {
      throw new BadRequestException(errorMessage.getInvalidRequest());
    }
    return delayCaseHistoryConverter.convertToPageResponse(delayCaseHistoryPage);
  }

  @Override
  public List<CaseMinimalResponse> getDelayCaseMasterData() {
    /*    return minimalResponseConverter.convertToCaseMinimalResponse(
    delayCaseHistoryRepository.findAllDistinctCases());*/
    return new ArrayList<>();
  }

  @Override
  public List<String> getLinkedCasesOfSample(Long caseId, Long sampleId) {
    return caseSampleMappingRepository.getAllLinkedCasesOfSample(caseId, sampleId).stream()
        .map(Cases::getCaseNumber)
        .collect(Collectors.toList());
  }

  public List<Cases> getCasesInGroupId(List<Long> groupIdList) {
    return caseRepo.findByGroupIdIn(groupIdList);
  }

  @Override
  public void closeCasesAsGroup(
      Long caseId, List<Long> selectedReportIds, boolean isCaseClose, UserMaster user) {
    List<Cases> linkedCasesInAnr = getAllLinkedCasesInAnr(caseId, false);
    checkCaseStatusInAncAndFaultTolerance(user, linkedCasesInAnr);
    List<Report> allReports =
        reportRepository.findByCaseIdInAndParentIsNullAndIsActiveTrue(linkedCasesInAnr);
    if (CollectionUtils.isEmpty(selectedReportIds)) {
      allReports.forEach(r -> r.setSendReportToClosing(Boolean.TRUE));
    } else {
      allReports.stream()
          .filter(r -> selectedReportIds.contains(r.getId()))
          .forEach(r -> r.setSendReportToClosing(Boolean.TRUE));
    }
    reportRepository.saveAll(allReports);

    closeLinkedCasesAsync(isCaseClose, user, linkedCasesInAnr);
  }

  /**
   * Closes linked cases asynchronously.
   *
   * <p>This method processes a list of linked cases and performs the close operation on each case
   * concurrently using asynchronous tasks managed by a {@link CompletableFuture}. It handles
   * request-scoped data by injecting the current request attributes into each newly spawned thread.
   *
   * <p>The method iterates through the provided list of {@link Cases}. For each case, it creates an
   * asynchronous task that calls the close case method. This allows the close operations to happen
   * in parallel, potentially improving performance.
   *
   * <p>Request attributes are captured before the asynchronous tasks are created and then set
   * within each task's execution context. This ensures that request-scoped data is available within
   * the {@link #closeCase} method, even though it's running in a separate thread. After each task
   * completes, the request attributes are reset to prevent leaks and interference between threads.
   *
   * @param isCaseClose A flag indicating whether to perform the actual case closure. If false, the
   *     close operation may be simulated or skipped.
   * @param user The user initiating the case closure operation.
   * @param linkedCasesInAnr The list of linked cases to be closed.
   */
  private void closeLinkedCasesAsync(
      boolean isCaseClose, UserMaster user, List<Cases> linkedCasesInAnr) {
    List<CompletableFuture<Void>> futures = new ArrayList<>();
    RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
    linkedCasesInAnr.forEach(
        aCase -> {
          futures.add(
              CompletableFuture.runAsync(
                  () -> {
                    try {
                      RequestContextHolder.setRequestAttributes(requestAttributes);
                      closeCase(isCaseClose, aCase, user);
                    } finally {
                      RequestContextHolder.resetRequestAttributes();
                    }
                  },
                  asyncExecutor));
        });
    CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
  }

  private void checkCaseStatusInAncAndFaultTolerance(UserMaster user, List<Cases> results) {
    results.forEach(
        aCase -> {
          if (Boolean.TRUE.equals(failedApiStatusCalls(aCase.getCaseId()))) {
            throw new BadRequestException(errorMessage.getFailedApiInRetry());
          }
          if (!Boolean.TRUE.equals(checkForCaseStatus(aCase.getCaseId(), user))) {
            throw new BadRequestException(errorMessage.getCaseStatusMisMatch());
          }
        });
  }

  @Override
  public Boolean overrideCaseStatus(CaseStatus caseStatus, Long caseId) {
    UserMaster user = userService.parseUserFromToken(request);
    Cases aCase = getCase(caseId);
    List<Cases> linkedCaseList = getAllLinkedCasesInAnr(caseId, false);
    linkedCaseList.forEach(linkedCase -> linkedCase.setOriginalCaseStatus(caseStatus));
    caseRepo.saveAll(linkedCaseList);
    aCase.setCaseStatus(caseStatus);
    caseRepo.save(aCase);
    saveCaseHistory(aCase.getCaseId(), user, caseStatus.getStatus());
    updateCaseGroupStatus(aCase, true, user, null);
    return aCase.getCaseStatus().equals(caseStatus);
  }

  private void closeCase(Boolean isCaseClose, Cases aCase, UserMaster user) {
    updateSignOutDate(user, aCase, new Date(), null);
    aCase = proceedToClose(aCase, isCaseClose, user);
    if (isCaseClose) {
      reportTemplateService.moveCaseReportsToClose(aCase, user);
    }
  }

  @Override
  public CasePageResponse getDedicatedCaseList(String filter, List<Integer> caseStatusIds) {
    String decode = URLDecoder.decode(filter, StandardCharsets.UTF_8);
    try {
      CaseFilterRequest caseFilterRequest = objectMapper.readValue(decode, CaseFilterRequest.class);
      int pageNumber = caseFilterRequest.getPageNumber();
      // Frontend starts page from 1
      pageNumber = pageNumber - 1;
      Pageable pageable =
          PageRequest.of(
              pageNumber,
              caseFilterRequest.getPageSize(),
              Sort.by(ASC, Cases_.CURRENT_DUE_DATE).and(Sort.by(ASC, Cases_.CASE_NUMBER)));
      CaseFilterDataResponse caseFilterData = getCaseFilterData(caseFilterRequest);
      CaseSearchRequest caseSearchRequest =
          buildCaseSearchRequest(caseFilterRequest, caseFilterData);
      caseSearchRequest.setCaseStatuses(caseStatusIds);
      Page<Cases> cases = caseRepo.findAll(CaseSpecification.getCases(caseSearchRequest), pageable);
      return casePageResponseConverter.convert(cases);
    } catch (JsonProcessingException e) {
      throw new BadRequestException(errorMessage.getInvalidRequest());
    }
  }

  @Override
  public NotaryMasterDetails getNotaryMasterDetails() {
    List<UserMaster> notaryUsers = userService.getUsersByRole("NOTARY");
    String notaryOauthString = plateService.getGeneralSetting("notaryOauthText");
    return NotaryMasterDetails.builder()
        .notaryUsers(
            notaryUsers.stream()
                .map(
                    userMaster ->
                        MinimalUserResponse.builder()
                            .userId(userMaster.getUserId())
                            .name(userMaster.getName())
                            .build())
                .distinct()
                .toList())
        .oauthText(notaryOauthString)
        .build();
  }

  @Override
  @Transactional
  public void bulkCaseSign(List<Long> caseIds, UserMaster user) {
    List<Cases> caseList = caseRepo.findByCaseIdIn(caseIds);
    for (Cases aCase : caseList) {
      saveToCaseReviewLog(aCase, CASE_REVIEW_TYPE_PHD, user);
      updateSignOutDate(user, aCase, new Date(), null);
    }
  }

  @Override
  public void assignToNotaryQueue(List<Long> caseIds, UserMaster notaryUser, UserMaster user) {
    List<Cases> caseList = caseRepo.findByCaseIdIn(caseIds);
    for (Cases caseData : caseList) {
      List<Cases> allLinkedCasesInAnr = getAllLinkedCasesInAnr(caseData.getCaseId(), false);
      allLinkedCasesInAnr.forEach(
          aCase -> {
            aCase.setCaseStatus(CaseStatus.NOTARY_QUEUE);
            aCase.setCaseProgressStatus(CaseProgressStatus.IN_PROGRESS);
            aCase.setCaseAssignee(notaryUser);
            aCase.setModifiedUser(user);
            aCase.setModifiedDate(Instant.now());
            saveCaseHistory(aCase.getCaseId(), user, CaseStatus.NOTARY_QUEUE.getStatus());
          });
    }
    caseRepo.saveAll(caseList);
  }

  @Override
  @Transactional
  public void applyNotarySeal(NotarySealAndCloseCasePayload payload, UserMaster user) {
    if (!userService.isUserInParticularRole(user, AppConstants.NOTARY_ROLE)) {
      throw new BadRequestException("Notary user required");
    }
    if (!userService.isNotaryPasswordValid(payload.getNotaryPassword(), user)) {
      throw new BadRequestException("Invalid notary password!");
    }
    //    if (!userService.isNotarySealFound(user)) {
    //      throw new BadRequestException("Notary seal not found");
    //    }
    List<Cases> caseList = caseRepo.findByCaseIdIn(payload.getCaseIds());
    for (Cases aCase : caseList) {
      if (aCase.getCaseType() == CaseType.CHAIN) {
        saveToCaseReviewLog(aCase, CASE_REVIEW_TYPE_NOTARY, user);
        //        closeCasesAsGroup(aCase.getCaseId(), true, user);
      }
    }
    caseRepo.saveAll(caseList);
  }

  @Override
  @Transactional
  public void closeCaseGroup(List<Long> caseIds, UserMaster user) {
    if (!userService.isUserInParticularRole(user, AppConstants.NOTARY_ROLE)) {
      throw new BadRequestException("Notary user required");
    }
    //    if (!userService.isNotarySealFound(user)) {
    //      throw new BadRequestException("Notary seal not found");
    //    }
    List<Cases> caseList = caseRepo.findByCaseIdIn(caseIds);
    for (Cases aCase : caseList) {
      if (aCase.getCaseType() == CaseType.CHAIN) {
        Optional<CaseReviewLog> notarySigned = getCaseReviewLog(aCase, CASE_REVIEW_TYPE_NOTARY);
        if (notarySigned.isEmpty())
          throw new BadRequestException(
              String.format("Notary not sealed on %s", aCase.getCaseNumber()));
        closeCasesAsGroup(aCase.getCaseId(), List.of(), true, user);
      }
    }
    caseRepo.saveAll(caseList);
  }

  @Override
  @Transactional
  public void updateRaceForPiCalc(Long caseId, Long reportId, String race, UserMaster user) {
    piSupport.setRecalculate(true);
    Cases aCase = getCase(caseId);
    List<Report> reports = reportService.getReportsByParentIsNull(aCase);
    TestPartyRace testPartyRace =
        testPartyRaceRepository
            .findByRace(race)
            .orElseThrow(() -> new NotFoundException(String.format("Race %s not found", race)));
    if (aCase.getIsNippTestType()) {
      recalcRaceForNipp(reportId, reports, testPartyRace, aCase);
    } else {
      reports.forEach(
          r -> {
            piSupport.getReportRaceUpdateMap().put(r.getId(), Boolean.TRUE);
          });
      for (var report : reports.stream().filter(el -> el.getId().equals(reportId)).toList()) {
        report.setTestPartyRace(testPartyRace);
        reportRepository.save(report);
        piService.reCalculatePiByCaseIdAndReport(user, aCase.getCaseId(), report);
        reportService.deleteSavedInterpretationAndDisclaimers(List.of(report));
      }
    }
  }

  /**
   * Recalculates race for NIPP and reschedules NIPP case data processing with updated race. This
   * method updates the test party race for a specific report, deletes associated interpretations
   * and disclaimers, and triggers the processing of NIPP case data with updated race.
   */
  private void recalcRaceForNipp(
      Long reportId, List<Report> reports, TestPartyRace testPartyRace, Cases aCase) {

    deleteOldInterpretationAndUpdateReportRace(reportId, reports, testPartyRace);
    /*Retrieve plate and plate run information*/
    Plates plate = plateService.getPlate(aCase.getPlate().getPlateId());
    PlateRunData plateRun =
        plateService.getPlateRunByPlateId(plate.getPlateId()).stream()
            .findFirst()
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "No PlateRunData found for plate ID: " + plate.getPlateId()));

    /*get data from nipp processed folder for updated race calc*/
    SmbFile caseFile = fetchNippCaseFileFromNippProcessedLocation(aCase, plateRun);
    NippCaseDetails nippCaseDetails =
        nippCaseDetailsRepository.findByCaseDataAndPlateAndStatusTrue(aCase, plate).orElse(null);

    if (nippCaseDetails != null && caseFile != null) {
      /*nippCaseData object from caseFile*/
        NippCaseData nippCaseData = getNippCaseData(caseFile);

      /*fetch alleged father sample data*/
      CaseSampleMapping caseSample =
          getSamplesFromCase(aCase.getCaseId()).stream()
              .filter(
                  sample ->
                      StringUtils.equals(
                          sample.getTestPartyRole().getAcronym(), ALLEGED_FATHER_ACRONYM))
              .findFirst()
              .orElseThrow(
                  () ->
                      new IllegalStateException(
                          "No alleged father sample found for case ID: " + aCase.getCaseId()));


      /*Update NIPP Case Data and Calc Sheet with the new race information*/
      Report report = reportService.getReport(reportId);
      updateNippCaseDataWithUpdatedRaceForAllegedSampleReports(
          nippCaseData, caseSample, report, nippCaseDetails);
      updateNippCalcSheetWithUpdatedRaceForAllegedSampleReports(
          aCase, nippCaseData, caseSample, report, plate);

      caseRepo.save(aCase);
      nippCaseDetailsRepository.save(nippCaseDetails);
    } else {
      throw new NotFoundException("Nipp case details to recalculate not found.");
    }
  }

  /**
   * Deserializes a {@link SmbFile} to {@link NippCaseData}.
   *
   * @param caseFile The SMB file representing NIPP case data.
   * @return The {@link NippCaseData} deserialized from the file.
   * @throws RuntimeException if an {@link IOException} occurs during deserialization.
   */
  private NippCaseData getNippCaseData(SmbFile caseFile) {
    try {
      ObjectMapper objectMapper = new ObjectMapper();
      return objectMapper.readValue(caseFile.getInputStream(), NippCaseData.class);
    } catch (IOException e) {
      throw new RuntimeException("Error deserializing NippCaseData from file: " + caseFile, e);
    }
  }

  /**
   * Updates {@link NippCaseData} and {@link NippCaseDetails} with paternity test result based on
   * report race for the alleged sample.
   *
   * @param nippCaseData The NIPP case data to update.
   * @param caseSample Mapping between case and sample.
   * @param report The report related to this paternity test.
   * @param nippCaseDetails Case details to update.
   */
  private void updateNippCaseDataWithUpdatedRaceForAllegedSampleReports(
      NippCaseData nippCaseData,
      CaseSampleMapping caseSample,
      Report report,
      NippCaseDetails nippCaseDetails) {
    getAllegedFatherSamplesReport(nippCaseData, caseSample.getNippId())
        .ifPresent(
            allegedSamplesReport ->
                getPaternityTestResultBasedOnReportRace(
                        allegedSamplesReport.getPaternityTests(),
                        report.getTestPartyRace().getRace())
                    .ifPresent(
                        testResult -> {
                          nippCaseDetails.setInterpretation(testResult.getPaternity());
                          nippCaseDetails.setSnps(testResult.getNumSnpsUsed());
                          nippCaseDetails.setCpi(testResult.getUsed_logCPI());
                        }));
  }

  /**
   * Updates {@link NippCaseCalcSheet} with calculated values for other alleged father samples in
   * {@link NippCaseData}.
   *
   * @param aCase The case data.
   * @param nippCaseData The NIPP case data.
   * @param caseSample Mapping between case and sample.
   * @param report The report related to this paternity test.
   * @param plate The plate information.
   */
  private void updateNippCalcSheetWithUpdatedRaceForAllegedSampleReports(
      Cases aCase,
      NippCaseData nippCaseData,
      CaseSampleMapping caseSample,
      Report report,
      Plates plate) {
    List<NippCaseData.AllegedSamplesReport> otherFatherSampleReports =
        nippCaseData.getAllegedSamplesReport().stream()
            .filter(
                sampleReport ->
                    !sampleReport
                        .getSequencingSampleReference()
                        .equalsIgnoreCase(caseSample.getNippId()))
            .toList();

    List<NippCaseCalcSheet> nippCaseCalcSheets =
        otherFatherSampleReports.stream()
            .map(
                fatherSampleReport -> {
                  NippCaseCalcSheet nippCaseCalcSheet = new NippCaseCalcSheet();
                  nippCaseCalcSheet.setCaseData(aCase);
                  nippCaseCalcSheet.setNippSampleId(
                      fatherSampleReport.getSequencingSampleReference());
                  nippCaseCalcSheet.setConcordanceResult(
                      fatherSampleReport.getQcSampleMetricsRollup());
                  nippCaseCalcSheet.setPlateId(plate.getPlateId());

                  getPaternityTestResultBasedOnReportRace(
                          fatherSampleReport.getPaternityTests(),
                          report.getTestPartyRace().getRace())
                      .ifPresent(
                          testResult -> {
                            nippCaseCalcSheet.setCpi(testResult.getUsed_logCPI());
                            nippCaseCalcSheet.setResult(testResult.getPaternity());
                          });
                  return nippCaseCalcSheet;
                })
            .toList();

    nippCaseCalcSheetRepository.saveAll(nippCaseCalcSheets);
  }

  /**
   * Fetches the NIPP case file from the processed location on the Samba share.
   *
   * @param aCase The case for which to fetch the file.
   * @param plateRun The plate run associated with the case.
   * @return The {@link SmbFile} representing the NIPP case file, or null if not found.
   * @throws RuntimeException if there's an error accessing the Samba share.
   */
  @Nullable
  private SmbFile fetchNippCaseFileFromNippProcessedLocation(Cases aCase, PlateRunData plateRun) {
    try {
      String sourcePath = sambaConfigProperties.getIp3() + sambaConfigProperties.getNippFolder();
      SmbFile basePath =
          new SmbFile(sourcePath, sambaConfiguration.getAuthenticationForNippFiles());

      Optional<SmbFile> plateDataFolder =
          Arrays.stream(basePath.listFiles())
              .filter(folder -> folder.getName().contains(plateRun.getPhysicalPlateId()))
              .findFirst();

      if (plateDataFolder.isEmpty()) {
        log.warn(
            "[race-recalc-nipp] Plate data folder not found for plate run: {}",
            plateRun.getPhysicalPlateId());
        return null;
      }

      SmbFile filesLocation =
          new SmbFile(
              plateDataFolder.get().getPath() + "classification/reports/",
              sambaConfiguration.getAuthenticationForNippFiles());

      return Arrays.stream(filesLocation.listFiles())
          .filter(
              file ->
                  file.getName().contains(aCase.getGroupId().toString())
                      && file.getName().contains("_report"))
          .findFirst()
          .orElse(null);

    } catch (MalformedURLException e) {
      throw new RuntimeException("Malformed URL Exception while accessing Samba share", e);
    } catch (SmbException e) {
      throw new RuntimeException("Smb Exception while accessing Samba share", e);
    }
  }

  private void deleteOldInterpretationAndUpdateReportRace(
      Long reportId, List<Report> reports, TestPartyRace testPartyRace) {
    reports.stream()
        .filter(el -> el.getId().equals(reportId))
        .toList()
        .forEach(
            report -> {
              report.setTestPartyRace(testPartyRace);
              reportService.deleteSavedInterpretationAndDisclaimers(reports);
            });
    reportRepository.saveAllAndFlush(reports);
  }

  @Override
  public Map<Long, TestPartyRole> getSampleIdTestPartyRoleMap(
      List<CaseSampleMapping> caseSampleMappings) {
    if (CollectionUtils.isEmpty(caseSampleMappings)) return new HashMap<>();
    return caseSampleMappings.stream()
        .collect(
            Collectors.groupingBy(
                csm -> csm.getSamples().getSampleId(),
                Collectors.mapping(
                    CaseSampleMapping::getTestPartyRole,
                    Collectors.collectingAndThen(
                        Collectors.toList(), list -> list.isEmpty() ? null : list.get(0)))));
  }

  @Transactional
  @Override
  public void saveNippCaseData(
      String groupId, NippCaseData nippCaseData, String fileName, Plates plate) {
    List<Cases> caseList = caseRepo.findByGroupId(Long.parseLong(groupId));
    if(nippCaseData == null) {
      caseList.stream().forEach(c -> {
        c.setCaseStatus(CaseStatus.NOT_READY);
        c.setOriginalCaseStatus(CaseStatus.NOT_READY);
      });
      return;
    }
    for (var aCase : caseList) {
      //      if (List.of(CaseStatus.NOT_READY, CaseStatus.AWAITING_TESTING)
      //          .contains(aCase.getCaseStatus())) {
      /*filters out previous inactive samples of the case to prevent this data being present
      for qc data in case view*/
      List<CaseSampleMapping> samplesFromCase =
          getSamplesFromCase(aCase.getCaseId()).stream()
              .filter(CaseSampleMapping::isActive)
              .toList();
      Report report = reportService.getLatestActiveReport(aCase);
      String race =
          report.getTestPartyRace() != null ? report.getTestPartyRace().getRace() : "Other";
      NippCaseDetails nippCaseDetails =
          nippCaseDetailsRepository
              .findByCaseDataAndPlateAndStatusTrue(aCase, plate)
              .orElse(new NippCaseDetails());
      List<NippCaseDetails> oldNippCaseDetails =
          nippCaseDetailsRepository.findByCaseDataAndPlateNotAndStatusTrue(aCase, plate);
      oldNippCaseDetails.forEach(nc -> nc.setStatus(Boolean.FALSE));
      nippCaseDetails.setCaseData(aCase);
      nippCaseDetails.setPlate(plate);
      nippCaseDetails.setFetusGender(nippCaseData.getFetusGender());
      nippCaseDetails.setOverriddenFetusGender(nippCaseData.getFetusGender());
      nippCaseDetails.setMotherConcordance(
          nippCaseData.getMother2Validation().getMother2Relatedness());
      nippCaseDetails.setMotherConcordanceFlag(
          nippCaseData.getMother2Validation().getMother2RelatednessFlag());
      nippCaseDetails.setMother2qcRollup(nippCaseData.getMother2Validation().getMother2qcRollup());
      nippCaseDetails.setMotherQCMetricsRollup(nippCaseData.getMotherQCMetricsRollup());
      nippCaseDetails.setStatus(Boolean.TRUE);
      for (var caseSample : samplesFromCase) {
        switch (caseSample.getTestPartyRole().getAcronym()) {
          case CHILD_FETUS_ACRONYM -> {
            createCaseSampleNippQCReport(nippCaseData.getQcReport(), caseSample, plate);
          }
          case MOTHER_ACRONYM -> {
            createCaseSampleNippQCReport(
                nippCaseData.getMother2Validation().getMother2qcReport(), caseSample, plate);
          }
          case ALLEGED_FATHER_ACRONYM -> {
            if (caseSample.getNippId() == null) {
              throw new BadRequestException(
                  String.format(
                      "Nipp ID not found for sample: %s", caseSample.getSamples().getSampleCode()));
            }

            // Alleged father sample processing
            Optional<NippCaseData.AllegedSamplesReport> fistFatherSampleReportOptional =
                getAllegedFatherSamplesReport(nippCaseData, caseSample.getNippId());
            fistFatherSampleReportOptional.ifPresent(
                allegedSamplesReport -> {
                  createCaseSampleNippQCReport(
                      allegedSamplesReport.getQcReport(), caseSample, plate);
                  nippCaseDetails.setAfConcordance(
                      allegedSamplesReport.getFather2Validation().getFather2Relatedness());
                  nippCaseDetails.setAfConcordanceFlag(
                      allegedSamplesReport.getFather2Validation().getFather2RelatednessFlag());
                  Optional<NippCaseData.PaternityTest> testResultOptional =
                      getPaternityTestResultBasedOnReportRace(
                          allegedSamplesReport.getPaternityTests(), race);
                  testResultOptional.ifPresent(
                      testResult -> {
                        nippCaseDetails.setInterpretation(testResult.getPaternity());
                        nippCaseDetails.setSnps(testResult.getNumSnpsUsed());
                        nippCaseDetails.setCpi(testResult.getUsed_logCPI());
                      });
                  try {
                    nippCaseDetails.setPaternityResultData(
                        objectMapper.writeValueAsString(allegedSamplesReport.getPaternityTests()));
                  } catch (JsonProcessingException e) {
                    throw new BadRequestException("Paternity report result data is invalid!");
                  }
                  nippCaseDetails.setFather2qcRollup(
                      allegedSamplesReport.getFather2Validation().getFather2qcRollup());
                  nippCaseDetails.setQcSampleMetricsRollup(
                      allegedSamplesReport.getQcSampleMetricsRollup());
                  // Alleged father confirmation sample qc processing
                  Optional<CaseSampleMapping> afcSampleMapping =
                      samplesFromCase.stream()
                          .filter(
                              s ->
                                  s.getTestPartyRole()
                                      .getAcronym()
                                      .equalsIgnoreCase(ALLEGED_FATHER_CONFIRMATION_ACRONYM))
                          .findFirst();
                  afcSampleMapping.ifPresent(
                      afcSample -> {
                        createCaseSampleNippQCReport(
                            allegedSamplesReport.getFather2Validation().getFather2qcReport(),
                            afcSample,
                            plate);
                      });
                });

            String[] split = caseSample.getNippId().split("-");
            String nippSampleIdLastPart = split[split.length - 1];
            List<NippCaseData.AllegedSamplesReport> otherFatherSampleReports =
                nippCaseData.getAllegedSamplesReport().stream()
                    .filter(
                        sampleReport ->
                            !sampleReport
                                .getSequencingSampleReference()
                                .equalsIgnoreCase(caseSample.getNippId()))
                    .toList();
            for (var fatherSampleReport : otherFatherSampleReports) {
              NippCaseCalcSheet nippCaseCalcSheet = new NippCaseCalcSheet();
              nippCaseCalcSheet.setCaseData(aCase);
              nippCaseCalcSheet.setNippSampleId(fatherSampleReport.getSequencingSampleReference());
              nippCaseCalcSheet.setConcordanceResult(fatherSampleReport.getQcSampleMetricsRollup());
              Optional<NippCaseData.PaternityTest> testResultOptional =
                  getPaternityTestResultBasedOnReportRace(
                      fatherSampleReport.getPaternityTests(), race);
              testResultOptional.ifPresent(
                  testResult -> {
                    nippCaseCalcSheet.setCpi(testResult.getUsed_logCPI());
                    nippCaseCalcSheet.setResult(testResult.getPaternity());
                  });
              nippCaseCalcSheet.setPlateId(plate.getPlateId());
              nippCaseCalcSheetRepository.save(nippCaseCalcSheet);
            }
          }
        }
      }
      caseRepo.save(aCase);
      nippCaseDetailsRepository.save(nippCaseDetails);
      nippCaseDetailsRepository.saveAll(oldNippCaseDetails);
      caseQCFactory.setPreviousCaseAlertInactive(aCase);
      UserMaster user = userService.getCurrentAuthenticatedUser();
      possibleSampleSwitchNippQc.validateCaseQC(
          aCase, null, user, aCase.getTestTypeSubCategory().getId());
      caseMetricFailureQc.validateCaseQC(aCase, null, user, aCase.getTestTypeSubCategory().getId());
      concordanceFailureNippQc.validateCaseQC(
          aCase, null, user, aCase.getTestTypeSubCategory().getId());

      //      }
    }
  }

  @NotNull
  @Override
  public Optional<NippCaseData.PaternityTest> getPaternityTestResultBasedOnReportRace(
      List<NippCaseData.PaternityTest> resultList, String race) {
    if (CollectionUtils.isEmpty(resultList)) return Optional.empty();
    Optional<NippCaseData.PaternityTest> testResultOptional =
        resultList.stream()
            .filter(result -> result.getEthnicityInput().equalsIgnoreCase(race))
            .findFirst();
    if (testResultOptional.isEmpty()) {
      testResultOptional =
          resultList.stream()
              .filter(result -> result.getEthnicityInput().equalsIgnoreCase("Other"))
              .findFirst();
    }
    return testResultOptional;
  }

  @NotNull
  private static Optional<NippCaseData.AllegedSamplesReport> getAllegedFatherSamplesReport(
      NippCaseData nippCaseData, String nippId) {
    return nippCaseData.getAllegedSamplesReport().stream()
        .filter(
            sampleReport -> sampleReport.getSequencingSampleReference().equalsIgnoreCase(nippId))
        .findFirst();
  }

  private void createCaseSampleNippQCReport(
      List<NippPlateQcData.QcReport> qcReports, CaseSampleMapping caseSample, Plates plate) {
    for (var qcReport : qcReports) {
      NippQCReport nippQCReport = plateQCProcessService.createNippQCReport(qcReport);
      CaseSampleNippQCMapping caseSampleNippQCMapping = new CaseSampleNippQCMapping();
      caseSampleNippQCMapping.setCaseSample(caseSample);
      caseSampleNippQCMapping.setNippQCReport(nippQCReport);
      caseSampleNippQCMapping.setPlateId(plate.getPlateId());
      caseSampleNippQCMappingRepository.save(caseSampleNippQCMapping);
    }
  }

  @Override
  public NippCaseQcResponse getNippCaseQcResponse(Long caseId) {
    Cases aCase = getCase(caseId);
    List<CaseSampleNippQCMapping> allQcReports =
        caseSampleNippQCMappingRepository.findByCase(aCase);
    List<NippCaseCalcSheet> allCalcSheets = nippCaseCalcSheetRepository.findByCaseData(aCase);

    Map<Long, List<CaseSampleNippQCMapping>> sequenceQcReportMap =
        allQcReports.stream().collect(Collectors.groupingBy(CaseSampleNippQCMapping::getPlateId));
    Map<Long, List<NippCaseCalcSheet>> sequenceCalcSheetMap =
        allCalcSheets.stream().collect(Collectors.groupingBy(NippCaseCalcSheet::getPlateId));

    NippCaseQcResponse nippCaseQcResponse = new NippCaseQcResponse();
    List<NippCaseQcResponse.CaseQcReportDetails> sampleCaseQcMapList = new ArrayList<>();
    List<NippCaseQcResponse.CaseCalcSheet> calcSheetResponseList = new ArrayList<>();
    for (var sequence : sequenceQcReportMap.keySet()) {
      List<CaseSampleNippQCMapping> qcReports = sequenceQcReportMap.get(sequence);
      Map<String, List<CaseSampleNippQCMapping>> sampleCodeQcMapping =
          qcReports.stream()
              .collect(Collectors.groupingBy(csqm -> csqm.getCaseSample().getNippId()));

      Map<String, List<NippCaseQcResponse.CaseQcReport>> sampleCaseQcMap = new HashMap<>();
      for (var nippSampleId : sampleCodeQcMapping.keySet()) {
        List<NippCaseQcResponse.CaseQcReport> qcReportList =
            sampleCodeQcMapping.get(nippSampleId).stream()
                .map(
                    sqm -> {
                      NippQCReport qc = sqm.getNippQCReport();
                      String qcDescription = getQcDescription(qc);
                      return NippCaseQcResponse.CaseQcReport.builder()
                          .name(qc.getQcName())
                          .value(qc.getValue())
                          .description(qcDescription)
                          .status(qc.getThresholdFlag())
                          .build();
                    })
                .toList();
        sampleCaseQcMap.put(nippSampleId, qcReportList);
      }
      sampleCaseQcMapList.add(
          NippCaseQcResponse.CaseQcReportDetails.builder()
              .plateId(sequence)
              .qcReport(sampleCaseQcMap)
              .build());
    }

    for (var sequence : sequenceCalcSheetMap.keySet()) {
      List<NippCaseCalcSheet> calcSheets = sequenceCalcSheetMap.get(sequence);
      List<NippCaseQcResponse.CalcSheet> calcSheetResponse =
          calcSheets.stream()
              .map(
                  record -> {
                    NippCaseDetails nippCaseDetails = new NippCaseDetails();
                    nippCaseDetails.setCpi(record.getCpi());
                    nippCaseDetails.setInterpretation(record.getResult());
                    String probability = getNippProbability(nippCaseDetails);
                    return NippCaseQcResponse.CalcSheet.builder()
                        .sampleCode(record.getNippSampleId())
                        .probability(probability)
                        .cpi(record.getCpi())
                        .concordanceResult(record.getConcordanceResult())
                        .result(nippCaseResult(record.getResult()).getRptResultType())
                        .build();
                  })
              .toList();
      calcSheetResponseList.add(
          NippCaseQcResponse.CaseCalcSheet.builder()
              .plateId(sequence)
              .calcSheets(calcSheetResponse)
              .build());
    }
    Collections.reverse(sampleCaseQcMapList);
    Collections.reverse(calcSheetResponseList);
    nippCaseQcResponse.setSampleQcReport(sampleCaseQcMapList);
    nippCaseQcResponse.setCaseCalcSheets(calcSheetResponseList);
    return nippCaseQcResponse;
  }

  @NotNull
  private static String getQcDescription(NippQCReport qc) {
    String qcDescription = "";
    if (qc.getFailAbove() != null && qc.getFailBelow() != null) {
      qcDescription = "Must be between " + qc.getFailBelow() + " and " + qc.getFailAbove();
    } else if (qc.getFailAbove() != null) {
      qcDescription = "Must be less than " + qc.getFailAbove();
    } else if (qc.getFailBelow() != null) {
      qcDescription = "Must be greater than or equal to " + qc.getFailBelow();
    }
    return qcDescription;
  }

  @Override
  public List<ScatterPlotFileListResponse> getScatterPlotFileList(
      String nippSampleId, String caseNumber, Long sequenceNumber) {
    Cases aCase =
        getCaseByCaseNumber(caseNumber)
            .orElseThrow(() -> new BadRequestException("Case not found"));
    List<ScatterPlotFileListResponse> scatterPlotFileListResponseList = new ArrayList<>();
    String basePath = getBasePathForScatterPlotsByCaseNumber(caseNumber, sequenceNumber);
    List<SamplePlateRunMapping> samplePlateRuns =
        plateService.getSamplePlateRunsByPlateId(sequenceNumber);
    List<CaseSampleMapping> caseSampleMappings =
        caseSampleMappingRepository.findByCaseDataCaseId(aCase.getCaseId());
    List<String> fetusSampleCodes =
        caseSampleMappings.stream()
            .filter(csm -> Objects.equals(csm.getTestPartyRole().getId(), FETUS_ID))
            .map(csm -> csm.getSamples().getSampleCode())
            .collect(Collectors.toList());
    Samples activeNippSample =
        samplePlateRuns.stream()
            .map(SamplePlateRunMapping::getSample)
            .filter(s -> fetusSampleCodes.contains(s.getSampleCode()))
            .findFirst()
            .orElseThrow(() -> new BadRequestException("Nipp Sample not found"));
    CaseSampleMapping caseSample =
        caseSampleMappings.stream()
            .filter(
                csm ->
                    Objects.equals(
                        csm.getSamples().getSampleCode(), activeNippSample.getSampleCode()))
            .findFirst()
            .orElseThrow(() -> new BadRequestException("Case sample not found"));
    fileRetriever
        .getFilePaths(
            caseSample.getNippId(),
            basePath,
            sambaConfiguration.getAuthentication(),
            sambaConfigProperties.getMountFlag())
        .forEach(
            (filePath, timestamp) ->
                scatterPlotFileListResponseList.add(
                    new ScatterPlotFileListResponse(filePath, timestamp, "scatter plot")));
    return FileSorter.sortFiles(scatterPlotFileListResponseList);
  }

  @Override
  @NotNull
  public String getBasePathForScatterPlotsByCaseNumber(String caseNumber, Long sequenceNumber) {
    Optional<PlateRunData> plateRunDataList =
        samplePlateRunRepository
            .findFirstByaCaseCaseNumberAndPlateRunPlatePlateIdOrderByCreatedDateDesc(
                caseNumber, sequenceNumber)
            .map(el -> el.getPlateRun());
    try {
      String analysisPlateId = plateRunDataList.get().getAnalysisPlateId();
      return scheduleJobService.getDestinationPlotPathForReading(analysisPlateId);
    } catch (Exception e) {
      throw new NotFoundException("Plot not found: " + e.getMessage());
    }
  }

  /**
   * Retrieves the file content as a byte array based on mount flag.
   *
   * @param fileName The name of the file to retrieve.
   * @param caseNumber The case number for the file.
   * @param sequenceNumber The sequence number for the file.
   * @return The file content as a base64 encoded string, or null if not found or error occurs.
   */
  @Override
  public String getFileContentByName(String fileName, String caseNumber, Long sequenceNumber) {
    String basePath = getBasePathForScatterPlotsByCaseNumber(caseNumber, sequenceNumber);

    return Boolean.TRUE.equals(sambaConfigProperties.getMountFlag())
        ? getFileContentFromMountedPath(basePath, fileName)
        : getFileContentFromSmb(basePath, fileName);
  }

  private String getFileContentFromMountedPath(String basePath, String fileName) {
    try {
      Path filePath = Paths.get(basePath, fileName);
      byte[] bytes = Files.readAllBytes(filePath);
      return Base64.getEncoder().encodeToString(bytes);
    } catch (IOException e) {
      log.error(
          "[plot file mounted path] Error reading file from mounted path: {}/{}",
          basePath,
          fileName,
          e);
      return null;
    }
  }

  private String getFileContentFromSmb(String basePath, String fileName) {
    try (SmbFileInputStream in =
        new SmbFileInputStream(
            new SmbFile(basePath + fileName, sambaConfiguration.getAuthentication()))) {
      byte[] bytes = in.readAllBytes();
      return Base64.getEncoder().encodeToString(bytes);
    } catch (Exception e) {
      log.error("[plot file smb] Error reading file from SMB: {}/{}", basePath, fileName, e);
      return null;
    }
  }

  @Override
  public List<CaseStatistics> getDashboardDetails(Date startDate, Date endDate) {
    List<CaseStatus> excludedCaseStatusList =
        List.of(
            CaseStatus.NOT_READY, CaseStatus.SUPERVISOR_REVIEW, CaseStatus.PHD_SUPERVISOR_REVIEW);
    List<CaseStatusCountProjection> caseStatusCountList =
        caseRepo.getCaseStatusCount(startDate, endDate, excludedCaseStatusList);
    Map<CaseStatus, CaseStatusCountProjection> CaseStatusCountProjectionMap =
        caseStatusCountList.stream()
            .collect(
                Collectors.toMap(
                    CaseStatusCountProjection::getCaseStatus,
                    Function.identity(),
                    (existing, replacement) -> replacement));
    List<CaseStatistics> caseStatisticsList = new ArrayList<>();
    List<CaseStatus> activeCaseStatusResponseList =
        List.of(
            CaseStatus.REVIEW_REQUIRED,
            CaseStatus.PHD_REVIEW,
            CaseStatus.AWAITING_TESTING,
            CaseStatus.SIGNATURE_QUEUE,
            CaseStatus.NOTARY_QUEUE,
            CaseStatus.MOVED_TO_CLOSE,
            CaseStatus.CASE_CLOSED);
    for (int i = 0; i < activeCaseStatusResponseList.size(); i++) {
      var status = activeCaseStatusResponseList.get(i);
      var statusCountItem = CaseStatusCountProjectionMap.get(status);
      CaseStatistics caseStatistics = new CaseStatistics();
      caseStatistics.setStatus(status.getStatus());
      caseStatistics.setOrder(i + 1);
      caseStatistics.setColor(status.getColor());
      caseStatistics.setIndex(status.getIndex());
      caseStatistics.setCount(0L);
      List<CaseStatistics.CaseTestTypeCount> caseTestTypeCountDataList = new ArrayList<>();
      if (statusCountItem != null) {
        List<CaseTestTypeCountProjection> caseTestTypeCountList =
            caseRepo.getCaseCountByTestTypeAndCaseStatus(
                statusCountItem.getCaseStatus(), startDate, endDate);
        caseStatistics.setCount(statusCountItem.getCount());
        for (var caseTestTypeCount : caseTestTypeCountList) {
          caseTestTypeCountDataList.add(
              CaseStatistics.CaseTestTypeCount.builder()
                  .testTypeName(caseTestTypeCount.getTestType().getTestType())
                  .count(caseTestTypeCount.getCount())
                  .build());
        }
      }
      caseStatistics.setTestTypeCounts(caseTestTypeCountDataList);
      caseStatisticsList.add(caseStatistics);
    }
    return caseStatisticsList;
  }

  @Override
  public AncCaseStatisticsResponse.AncCaseStatistics getAncDashboardCaseCountData(
      String startDate, String endDate) {
    AncCaseStatisticsResponse ancResponse =
        accessioningFeignClient.getDashboardCaseCount(startDate, endDate).getBody();
    if (ancResponse != null) {
      return ancResponse.getData();
    }
    return null;
  }

  @Override
  public DashboardCaseResponse getDashboardCaseList(String filter) {
    String decode = URLDecoder.decode(filter, StandardCharsets.UTF_8);
    try {
      CaseFilterRequest caseFilterRequest = objectMapper.readValue(decode, CaseFilterRequest.class);
      int pageNumber = caseFilterRequest.getPageNumber();
      // Frontend starts page from 1
      pageNumber = pageNumber - 1;
      Sort sort = Sort.by(ASC, Cases_.CURRENT_DUE_DATE).and(Sort.by(ASC, Cases_.CASE_NUMBER));
      if (caseFilterRequest.getSortDirection() != null
          && caseFilterRequest.getSortField() != null) {
        sort =
            Sort.by(
                caseFilterRequest.getSortDirection().equals("desc") ? DESC : ASC,
                caseFilterRequest.getSortField());
      }
      Pageable pageable = PageRequest.of(pageNumber, caseFilterRequest.getPageSize(), sort);
      CaseFilterDataResponse caseFilterData = getCaseFilterData(caseFilterRequest);
      CaseSearchRequest caseSearchRequest =
          buildCaseSearchRequest(caseFilterRequest, caseFilterData);
      caseSearchRequest.setDashboardCaseListing(true);
      caseSearchRequest.setCaseStatusesNotIn(
          List.of(
              CaseStatus.NOT_READY.ordinal(),
              CaseStatus.SUPERVISOR_REVIEW.ordinal(),
              CaseStatus.PHD_SUPERVISOR_REVIEW.ordinal()));
      Page<Cases> cases = caseRepo.findAll(CaseSpecification.getCases(caseSearchRequest), pageable);
      List<DashboardCaseResponse.CaseListResponse> caseListResponses =
          cases.getContent().stream()
              .map(
                  aCase -> {
                    return DashboardCaseResponse.CaseListResponse.builder()
                        .caseId(aCase.getCaseId())
                        .caseNumber(aCase.getGroupId())
                        .testId(aCase.getCaseNumber())
                        .dueDate(
                            aCase.getCurrentDueDate() != null
                                ? aCase.getCurrentDueDate()
                                : aCase.getDueDate())
                        .caseDefinition(aCase.getTestType().getTestType())
                        .caseStatus(aCase.getCaseStatus().getStatus())
                        .build();
                  })
              .toList();
      return DashboardCaseResponse.builder()
          .pageSize(cases.getTotalPages())
          .totalPages(cases.getTotalPages())
          .totalNoOfElements(cases.getTotalElements())
          .totalRecords(cases.getTotalElements())
          .data(caseListResponses)
          .build();
    } catch (JsonProcessingException e) {
      throw new BadRequestException(errorMessage.getInvalidRequest());
    }
  }

  @Override
  public DashboardCaseResponse getAncDashboardCaseList(String filter) {
    try {
      ResponseEntity<AncDashboardCaseListResponse> dashboardCaseList =
          accessioningFeignClient.getDashboardCaseListing(filter);
      List<DashboardCaseResponse.CaseListResponse> caseListResponses =
          dashboardCaseList.getBody().getData().getCaseList().stream()
              .map(
                  aCase -> {
                    return DashboardCaseResponse.CaseListResponse.builder()
                        .caseId(aCase.getCaseId())
                        .caseNumber(aCase.getCaseId())
                        .testId(String.valueOf(aCase.getTestId()))
                        .dueDate(aCase.getDueDate())
                        .caseDefinition(aCase.getTestType())
                        .caseStatus(aCase.getCaseStatus())
                        .build();
                  })
              .toList();
      return DashboardCaseResponse.builder()
          .pageSize(dashboardCaseList.getBody().getData().getPageSize())
          .totalPages(dashboardCaseList.getBody().getData().getTotalPages())
          .totalNoOfElements(dashboardCaseList.getBody().getData().getTotalNoOfElements())
          .data(caseListResponses)
          .build();
    } catch (Exception e) {
      throw new BadRequestException("Something went wrong with ANC case listing!");
    }
  }

  @Override
  public byte[] getDataFromSourceFile(SmbFile sourceFile) {
    byte[] egramDataByte;
    InputStream inputStream = null;
    try {
      inputStream = sourceFile.getInputStream();
      egramDataByte = inputStream.readAllBytes();
    } catch (IOException e) {
      throw new BadRequestException("Failed to read file from SMB share: " + e.getMessage());
    } finally {
      if (inputStream != null) {
        try {
          inputStream.close();
        } catch (IOException e) {
          log.info(e.getMessage());
        }
      }
    }
    return egramDataByte;
  }

  /**
   * Builds and sets the translation response based on provided data.
   *
   * @param caseReportLanguageList The list of case report languages.
   * @param aCase The current case.
   * @return The {@link TranslationResponse} object with populated translations.
   */
  @Override
  public List<TranslationResponse.Translation> setTranslationResponse(
      List<CaseReportLangugae> caseReportLanguageList, Cases aCase) {
    List<Long> activeLanguageIdsForTranslation =
        caseReportLanguageList.stream()
            .map(reportLanguage -> reportLanguage.getLanguage().getLanguageId())
            .toList();

    Set<String> activeSampleRaces =
        caseSampleMappingRepository.getActiveSampleRacesFromCase(aCase).stream()
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());

    List<TranslationResponse.Translation> translations = new ArrayList<>();
    for (Long languageId : activeLanguageIdsForTranslation) {
      List<TranslationResponse.RaceTranslation> raceTranslationsForLanguage = new ArrayList<>();
      for (String race : activeSampleRaces) {
        /*Find the translation for the current race and language*/
        String translatedValue = findRaceTranslation(race, languageId);
        raceTranslationsForLanguage.add(
            new TranslationResponse.RaceTranslation(race, translatedValue));
      }
      translations.add(
          new TranslationResponse.Translation(languageId, raceTranslationsForLanguage));
    }

    return translations;
  }

  /**
   * Finds the translated value for a specific race and language ID. Finds the translated value for
   * a specific race and language ID.
   *
   * @param race The race to translate.
   * @param languageId The target language ID.
   * @return The translated value, or null if not found.
   */
  private String findRaceTranslation(String race, Long languageId) {
    TemplateMasterField templateMasterField =
        templateMasterFieldRepository.findByTemplateFieldSlug(race);
    if (templateMasterField == null) return race;
    Integer templateFieldId = templateMasterField.getId();
    Optional<ReportTranslation> translation =
        Optional.of(
            reportTranslationRepository.findByTemplateFieldIdAndLanguageId(
                templateFieldId, languageId));

    return translation.map(ReportTranslation::getTranslationValue).orElse(null);
  }

  @Override
  public void overrideFetusGenderNipp(Long caseId, String fetusGender) {
    Cases aCase =
        Optional.of(getCase(caseId)).orElseThrow(() -> new NotFoundException("Case not found."));
    List<NippCaseDetails> nippCaseDetailsList = nippCaseDetailsRepository.findByCaseData(aCase);
    nippCaseDetailsList.forEach(
        nippCaseDetail -> nippCaseDetail.setOverriddenFetusGender(nippFetusGender(fetusGender)));
    nippCaseDetailsRepository.saveAll(nippCaseDetailsList);
    List<Report> reportsByCase = reportService.getReportsByCase(aCase);
    reportService.deleteSavedInterpretationAndDisclaimers(reportsByCase);
  }

  @Override
  public Object getPaternityQuestionnaire(Long caseNumber) {
    String auth = azureTokenUtils.getAzureTokenOfOutBoundAPI(null);
    return schedulingCaseFeignClient.getPaternityQuestionnaire(auth, caseNumber);
  }

  /**
   * To fetch all related / linked cases from the collection of case id's and initiate the close
   * linked cases as async operation
   *
   * @param caseIds unique collection of case ids
   * @param user user details
   */
  @Override
  public void sendCaseClosingReportAsAsync(Set<Long> caseIds, UserMaster user) {
    List<Cases> casesList =
        caseIds.stream()
            .map(caseId -> this.getAllLinkedCasesInAnr(caseId, false))
            .flatMap(List::stream)
            .toList();
    this.updateAllReportAsSendReportToClosingStatus(casesList);
    this.closeLinkedCasesAsync(true, user, casesList);
  }

  @Override
  public void updateCaseStatusAndReAssign(Long caseId, Integer statusId, UserMaster user) {
    if (!this.checkForSupervisorRole(user.getUserId()))
      throw new UserRoleMismatchException(
          "Restricted - User Role neither in PhD Supervisor nor Supervisor.");
    List<Cases> linkedCasesInAnr = getAllLinkedCasesInAnr(caseId, false);
    if (CollectionUtils.isEmpty(linkedCasesInAnr))
      throw new NotFoundException("Case details not found");
    CaseStatus caseStatus = CaseStatus.getCaseStatus(statusId);
    Objects.requireNonNull(caseStatus, "Invalid case status");
    if (!CaseStatus.isEnumConstantInList(
        caseStatus,
        CaseStatus.REVIEW_REQUIRED,
        CaseStatus.PHD_REVIEW,
        CaseStatus.SUPERVISOR_REVIEW)) {
      throw new BadRequestException(
          String.format(
              "Failed to update case status - Invalid case status '%s'", caseStatus.getStatus()));
    }
    linkedCasesInAnr.forEach(cases -> this.changeCaseStatus(cases, caseStatus.name(), user));
    this.updateCaseGroupStatus(this.getCase(caseId), false, user, null);
  }

  private void updateAllReportAsSendReportToClosingStatus(final List<Cases> cases) {
    List<Report> allReports = reportRepository.findByCaseIdInAndParentIsNullAndIsActiveTrue(cases);
    if (CollectionUtils.isEmpty(allReports))
      throw new NotFoundException("Failed to fetch the report details");
    allReports.forEach(r -> r.setSendReportToClosing(Boolean.TRUE));
    reportRepository.saveAll(allReports);
  }

  @Override
  public void setActivePlateRunFromCase(Long plateId, Long caseId, LoggedUser loggedUser) {
    try {
      Plates plate = plateService.getPlate(plateId);
      Cases aCase = getCase(caseId);
      aCase.setPlate(plate);
      List<NippCaseDetails> nippCaseDetails = nippCaseDetailsRepository.findByCaseData(aCase);
      nippCaseDetails.forEach(
          nippCaseDetail -> {
            if (nippCaseDetail.getPlate().getPlateId() == plate.getPlateId()) {
              nippCaseDetail.setStatus(true);
            } else {
              nippCaseDetail.setStatus(false);
            }
          });
      nippCaseDetailsRepository.saveAllAndFlush(nippCaseDetails);
      updateReportInterpretationsAndDisclaimersOfCase(aCase);
      caseRepo.save(aCase);
    } catch (Exception e) {
      throw new ServerException(e.getMessage());
    }
  }

  @Override
  public void updateUnderReviewStatus(Long caseId, Integer statusId, LoggedUser loggedUser) {
    Cases cases = Optional.ofNullable(caseRepo.getByCaseId(caseId))
                          .orElseThrow(() -> new NotFoundException("Case details not found"));
    cases.setIsUnderReview(CommonUtil.convertIntToBoolean(statusId));
    caseRepo.save(cases);
  }

  private void updateReportInterpretationsAndDisclaimersOfCase(Cases aCase) {
    List<Report> reports =
        reportRepository.findByCaseIdAndParentIsNullAndIsActiveTrueOrderByIdDesc(aCase);
    reportService.deleteSavedInterpretationAndDisclaimers(reports);
  }

  public List<RsData> getAllRsData() {
    List<RsMasterData> allRsMasterData = rsMasterDataRepository.findAll();

    // Fetch all RsGenotypeData in a single query
    List<RsGenotypeData> allGenotypeData = rsGenotypeDataRepository.findAll();

    // Group RsGenotypeData by rsMasterId
    Map<Long, List<RsGenotypeData>> genotypeDataByRsMasterId =
        allGenotypeData.stream()
            .collect(Collectors.groupingBy(genotypeData -> genotypeData.getRsMasterData().getId()));

    List<RsData> rsDataList = new ArrayList<>();

    for (RsMasterData rsMasterData : allRsMasterData) {
      List<RsGenotypeData> genotypeDataList =
          genotypeDataByRsMasterId.getOrDefault(rsMasterData.getId(), List.of());

      List<ControlRsValue> controlRsValues =
          genotypeDataList.stream()
              .map(
                  genotypeData -> {
                    String genotypeValue = genotypeData.getGenotypeValue();
                    String[] parts = genotypeValue.split("/");
                    String rsValue1 = parts[0];
                    String rsValue2 =
                        parts.length > 1 ? parts[1] : parts[0]; // Handle cases like 'C/C'

                    return new ControlRsValue(
                        genotypeData.getRsControlsMasterData().getControlNumber().toString(),
                        genotypeData.getRsControlsMasterData().getControlName(),
                        rsValue1,
                        rsValue2);
                  })
              .collect(Collectors.toList());

      RsData rsData =
          new RsData(rsMasterData.getRsId(), rsMasterData.getGeneVariant(), controlRsValues);
      rsDataList.add(rsData);
    }

    return rsDataList;
  }
}
