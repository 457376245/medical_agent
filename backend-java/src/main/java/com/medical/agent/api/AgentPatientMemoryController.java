package com.medical.agent.api;

import com.medical.agent.application.PatientMemoryService;
import com.medical.agent.domain.dto.ApiResponse;
import com.medical.agent.domain.dto.request.SubmitPatientMemoryEntriesRequest;
import com.medical.agent.domain.dto.response.PatientMemoryEntryListResponseData;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/agent/patient-memories")
@Tag(name = "Agent 患者画像记忆接口", description = "提供给 backend-agent 的患者画像候选记忆提交能力")
public class AgentPatientMemoryController {
  private final PatientMemoryService patientMemoryService;
  private final InternalAgentApiGuard internalAgentApiGuard;

  public AgentPatientMemoryController(
      PatientMemoryService patientMemoryService,
      InternalAgentApiGuard internalAgentApiGuard) {
    this.patientMemoryService = patientMemoryService;
    this.internalAgentApiGuard = internalAgentApiGuard;
  }

  @PostMapping
  @Operation(summary = "提交患者画像候选记忆", description = "由 Agent 在对话结束后提交结构化画像更新候选")
  public ApiResponse<PatientMemoryEntryListResponseData> submitMemories(
      @RequestBody SubmitPatientMemoryEntriesRequest request,
      HttpServletRequest httpRequest) {
    internalAgentApiGuard.verify(httpRequest);
    return new ApiResponse<>(
        "OK",
        "created",
        RequestIdUtil.newRequestId(),
        patientMemoryService.submitAgentMemories(request));
  }
}
