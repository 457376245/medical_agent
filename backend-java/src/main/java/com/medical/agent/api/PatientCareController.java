package com.medical.agent.api;

import com.medical.agent.application.PatientCareService;
import com.medical.agent.application.PatientMemoryService;
import com.medical.agent.domain.dto.ApiResponse;
import com.medical.agent.domain.dto.request.CreateFollowUpTaskRequest;
import com.medical.agent.domain.dto.request.PatientMemoryReviewRequest;
import com.medical.agent.domain.dto.request.CreateSymptomLogRequest;
import com.medical.agent.domain.dto.request.UpdateFollowUpTaskRequest;
import com.medical.agent.domain.dto.request.UpdatePatientCareProfileRequest;
import com.medical.agent.domain.dto.response.PatientCareEvidenceResponseData;
import com.medical.agent.domain.dto.response.PatientCareFollowUpTaskListResponseData;
import com.medical.agent.domain.dto.response.PatientCareProfileResponseData;
import com.medical.agent.domain.dto.response.PatientCareRiskOverviewResponseData;
import com.medical.agent.domain.dto.response.PatientCareSymptomLogListResponseData;
import com.medical.agent.domain.dto.response.PatientMemoryEntryListResponseData;
import com.medical.agent.domain.dto.response.PatientMemoryEntryResponseData;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/patient-care")
@Tag(name = "慢病随访支持", description = "患者长期画像、随访任务、症状记录和风险证据接口")
public class PatientCareController {
  private final PatientCareService patientCareService;
  private final PatientMemoryService patientMemoryService;

  public PatientCareController(
      PatientCareService patientCareService,
      PatientMemoryService patientMemoryService) {
    this.patientCareService = patientCareService;
    this.patientMemoryService = patientMemoryService;
  }

  @GetMapping("/profile")
  @Operation(summary = "查询慢病画像", description = "返回当前患者的长期病情画像、用药、目标和近期症状")
  public ApiResponse<PatientCareProfileResponseData> getProfile() {
    return new ApiResponse<>("OK", "success", RequestIdUtil.newRequestId(), patientCareService.getProfile());
  }

  @PutMapping("/profile")
  @Operation(summary = "更新慢病画像", description = "按当前患者维度更新长期健康画像")
  public ApiResponse<PatientCareProfileResponseData> updateProfile(@RequestBody UpdatePatientCareProfileRequest request) {
    return new ApiResponse<>("OK", "updated", RequestIdUtil.newRequestId(), patientCareService.upsertProfile(request));
  }

  @GetMapping("/follow-up-tasks")
  @Operation(summary = "查询随访任务", description = "按状态查询当前患者的行动任务清单")
  public ApiResponse<PatientCareFollowUpTaskListResponseData> listFollowUpTasks(
      @RequestParam(name = "status", required = false) String status,
      @RequestParam(name = "limit", required = false) Integer limit,
      @RequestParam(name = "profileId", required = false) String profileId) {
    return new ApiResponse<>("OK", "success", RequestIdUtil.newRequestId(), patientCareService.listFollowUpTasks(status, limit, profileId));
  }

  @PostMapping("/follow-up-tasks")
  @Operation(summary = "创建随访任务", description = "为当前患者新增一个复查或复诊动作项")
  public ApiResponse<PatientCareFollowUpTaskListResponseData.TaskSummary> createFollowUpTask(
      @RequestBody CreateFollowUpTaskRequest request) {
    return new ApiResponse<>("OK", "created", RequestIdUtil.newRequestId(), patientCareService.createFollowUpTask(request));
  }

  @PatchMapping("/follow-up-tasks/{taskId}")
  @Operation(summary = "更新随访任务", description = "更新任务状态、截止日期或备注")
  public ApiResponse<PatientCareFollowUpTaskListResponseData.TaskSummary> updateFollowUpTask(
      @PathVariable("taskId") String taskId,
      @RequestBody UpdateFollowUpTaskRequest request) {
    return new ApiResponse<>("OK", "updated", RequestIdUtil.newRequestId(), patientCareService.updateFollowUpTask(taskId, request));
  }

  @GetMapping("/symptoms")
  @Operation(summary = "查询症状/体征记录", description = "返回当前患者最近的症状或体征记录")
  public ApiResponse<PatientCareSymptomLogListResponseData> listSymptoms(
      @RequestParam(name = "limit", required = false) Integer limit,
      @RequestParam(name = "profileId", required = false) String profileId) {
    return new ApiResponse<>("OK", "success", RequestIdUtil.newRequestId(), patientCareService.listSymptoms(limit, profileId));
  }

  @PostMapping("/symptoms")
  @Operation(summary = "新增症状/体征记录", description = "记录本次随访需要关注的症状或家庭测量值")
  public ApiResponse<PatientCareSymptomLogListResponseData.SymptomLogItem> createSymptom(
      @RequestBody CreateSymptomLogRequest request) {
    return new ApiResponse<>("OK", "created", RequestIdUtil.newRequestId(), patientCareService.createSymptomLog(request));
  }

  @GetMapping("/risk-overview")
  @Operation(summary = "查询风险概览", description = "按当前档案或报告返回随访风险、红旗信号和证据来源")
  public ApiResponse<PatientCareRiskOverviewResponseData> getRiskOverview(
      @RequestParam(name = "profileId", required = false) String profileId,
      @RequestParam(name = "recordId", required = false) String recordId) {
    return new ApiResponse<>("OK", "success", RequestIdUtil.newRequestId(), patientCareService.getRiskOverview(profileId, recordId));
  }

  @GetMapping("/evidence")
  @Operation(summary = "查询证据引用", description = "返回当前上下文下可展示的规则、趋势和长期画像证据")
  public ApiResponse<PatientCareEvidenceResponseData> getEvidence(
      @RequestParam(name = "profileId", required = false) String profileId,
      @RequestParam(name = "recordId", required = false) String recordId) {
    return new ApiResponse<>("OK", "success", RequestIdUtil.newRequestId(), patientCareService.getEvidenceRefs(profileId, recordId));
  }

  @GetMapping("/memories")
  @Operation(summary = "查询画像候选记忆", description = "按状态查询当前患者的长期画像记忆账本")
  public ApiResponse<PatientMemoryEntryListResponseData> listMemories(
      @RequestParam(name = "status", required = false) String status,
      @RequestParam(name = "limit", required = false) Integer limit) {
    return new ApiResponse<>("OK", "success", RequestIdUtil.newRequestId(), patientMemoryService.listMemories(status, limit));
  }

  @PostMapping("/memories/{memoryId}/confirm")
  @Operation(summary = "确认画像候选记忆", description = "确认后将候选记忆合并进当前患者长期画像")
  public ApiResponse<PatientMemoryEntryResponseData> confirmMemory(
      @PathVariable("memoryId") String memoryId) {
    return new ApiResponse<>("OK", "updated", RequestIdUtil.newRequestId(), patientMemoryService.confirmMemory(memoryId));
  }

  @PostMapping("/memories/{memoryId}/reject")
  @Operation(summary = "拒绝画像候选记忆", description = "拒绝错误或不应进入长期画像的候选记忆")
  public ApiResponse<PatientMemoryEntryResponseData> rejectMemory(
      @PathVariable("memoryId") String memoryId,
      @RequestBody(required = false) PatientMemoryReviewRequest request) {
    return new ApiResponse<>(
        "OK",
        "updated",
        RequestIdUtil.newRequestId(),
        patientMemoryService.rejectMemory(memoryId, request == null ? null : request.reason()));
  }
}
