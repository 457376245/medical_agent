package com.medical.agent.infrastructure.audit;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
@Tag(name = "审计日志服务", description = "统一记录关键业务动作的结构化审计日志，支撑追踪、合规与问题排查")
public class AuditLogService {
  private static final Logger LOGGER = LoggerFactory.getLogger("AUDIT");

  @Operation(summary = "写入审计日志事件", description = "按统一字段格式落审计日志，包含动作、资源、结果与请求追踪信息")
  public void logEvent(String action, String resourceType, String resourceId, String outcome, String requestId) {
    Map<String, Object> event = Map.of(
        "ts", Instant.now().toString(),
        "action", action,
        "resourceType", resourceType,
        "resourceId", resourceId,
        "outcome", outcome,
        "requestId", requestId);
    LOGGER.info("{}", event);
  }
}
