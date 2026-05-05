package com.medical.agent.api;

import com.medical.agent.application.AgentDashboardService;
import com.medical.agent.domain.dto.ApiResponse;
import com.medical.agent.domain.dto.response.AgentDashboardResponseData;
import com.medical.agent.domain.dto.response.RecordRefResponseData;
import com.medical.agent.domain.vo.RecordTrendData;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/agent")
@Tag(name = "患者 Agent 总览", description = "患者慢病追踪与引导式咨询页面聚合接口")
public class AgentDashboardController {
  private final AgentDashboardService agentDashboardService;

  public AgentDashboardController(AgentDashboardService agentDashboardService) {
    this.agentDashboardService = agentDashboardService;
  }

  @GetMapping("/dashboard")
  @Operation(summary = "查询 Agent 疾病总览", description = "按当前患者和疾病档案聚合风险、报告、趋势、症状、用药和随访任务")
  public ApiResponse<AgentDashboardResponseData> getDashboard(
      @RequestParam(name = "profileId", required = false) String profileId) {
    return new ApiResponse<>(
        "OK",
        "success",
        RequestIdUtil.newRequestId(),
        agentDashboardService.getDashboard(profileId));
  }

  @GetMapping("/trends")
  @Operation(summary = "查询 Agent 分类趋势", description = "按当前患者、疾病档案和报告分类聚合同类报告指标趋势")
  public ResponseEntity<ApiResponse<?>> getTrends(
      @RequestParam(name = "profileId", required = false) String profileId,
      @RequestParam(name = "sourceType", required = false) String sourceType,
      @RequestParam(name = "limit", required = false) Integer limit) {
    String normalizedSourceType = sourceType == null ? "" : sourceType.trim();
    if (normalizedSourceType.isEmpty()) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiResponse<>(
          "INVALID_SOURCE_TYPE",
          "sourceType is required",
          RequestIdUtil.newRequestId(),
          new RecordRefResponseData(null)));
    }

    int normalizedLimit = limit == null ? 6 : Math.max(1, Math.min(limit, 6));
    RecordTrendData trendData =
        agentDashboardService.getTrendBySourceType(profileId, normalizedSourceType, normalizedLimit);
    return ResponseEntity.ok(new ApiResponse<>("OK", "success", RequestIdUtil.newRequestId(), trendData));
  }
}
