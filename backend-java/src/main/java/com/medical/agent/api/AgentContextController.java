package com.medical.agent.api;

import com.medical.agent.application.AgentDiseaseProfileContextService;
import com.medical.agent.domain.dto.response.AgentDiseaseProfileContextResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/internal/agent/profiles", "/internal/agent/disease-profiles"})
@Tag(name = "Agent 内部上下文接口", description = "提供给 backend-agent 的疾病档案聚合查询能力")
public class AgentContextController {
  private final AgentDiseaseProfileContextService profileContextService;

  public AgentContextController(AgentDiseaseProfileContextService profileContextService) {
    this.profileContextService = profileContextService;
  }

  @GetMapping("/{profileId}/context")
  @Operation(summary = "查询 Agent 疾病上下文", description = "按疾病档案和可选报告返回紧凑聚合上下文")
  public ResponseEntity<?> fetchProfileContext(
      @Parameter(description = "疾病档案 ID（UUID）")
      @PathVariable("profileId") String profileId,
      @Parameter(description = "可选报告 ID（UUID）")
      @RequestParam(name = "recordId", required = false) String recordId) {
    try {
      AgentDiseaseProfileContextResponse response = profileContextService.fetchProfileContext(profileId, recordId);
      return ResponseEntity.ok(response);
    } catch (AgentDiseaseProfileContextService.ContextException error) {
      HttpStatus status = HttpStatus.resolve(error.httpStatus());
      if (status == null) {
        status = HttpStatus.BAD_REQUEST;
      }
      return ResponseEntity.status(status).body(new AgentContextErrorResponse(error.code(), error.getMessage()));
    }
  }

  private record AgentContextErrorResponse(String code, String message) {}
}
