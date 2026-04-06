package com.medical.agent.infrastructure.mq;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medical.agent.application.service.ParseJobService;
import com.medical.agent.application.service.RecordService;
import com.medical.agent.domain.vo.GenerateRequestEvent;
import com.medical.agent.domain.vo.ParseJobContext;
import com.medical.agent.domain.vo.RecordAnalysisContext;
import com.medical.agent.domain.vo.StructuredResultData;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class ParseResultConsumer {
  private static final Logger LOGGER = LoggerFactory.getLogger(ParseResultConsumer.class);
  private final ObjectMapper objectMapper;
  private final ParseJobService parseJobService;
  private final RecordService recordService;
  private final GenerateRequestPublisher generateRequestPublisher;

  public ParseResultConsumer(
      ObjectMapper objectMapper,
      ParseJobService parseJobService,
      RecordService recordService,
      GenerateRequestPublisher generateRequestPublisher) {
    this.objectMapper = objectMapper;
    this.parseJobService = parseJobService;
    this.recordService = recordService;
    this.generateRequestPublisher = generateRequestPublisher;
  }

  @RabbitListener(queues = "agent.parse.result.v1")
  public void consume(String payload) {
    try {
      Map<String, Object> event = objectMapper.readValue(payload, new TypeReference<>() {});
      UUID jobId = UUID.fromString(String.valueOf(event.get("jobId")));
      String status = String.valueOf(event.getOrDefault("status", "FAILED"));
      Object structured = event.getOrDefault("structuredResult", Map.of("fields", java.util.List.of()));
      String structuredJson = objectMapper.writeValueAsString(structured);
      double confidence = Double.parseDouble(String.valueOf(event.getOrDefault("confidence", 0.0)));
      String errorCode = null;
      Object errors = event.get("errors");
      if (errors instanceof java.util.List<?> errorList && !errorList.isEmpty() && errorList.get(0) instanceof Map<?, ?> firstError) {
        Object code = firstError.get("code");
        if (code != null) {
          errorCode = String.valueOf(code);
        }
      }
      ParseJobService.ParseApplyResult applyResult =
          parseJobService.applyParseResult(jobId, status, structuredJson, confidence, errorCode);
      LOGGER.info("Applied parse result for jobId={} status={} finalStatus={}", jobId, status, applyResult.finalStatus());
      if (applyResult.stateChanged() && "SUCCESS".equals(applyResult.finalStatus())) {
        Object classifiedRaw = event.get("classifiedSourceType");
        String classifiedSourceType = classifiedRaw != null ? String.valueOf(classifiedRaw).trim() : null;
        if (classifiedSourceType != null && !classifiedSourceType.isBlank()) {
          try {
            recordService.applyAutoClassification(applyResult.recordId(), classifiedSourceType);
            LOGGER.info("Auto-classified record {} as '{}'", applyResult.recordId(), classifiedSourceType);
          } catch (Exception ex) {
            LOGGER.warn("Failed to apply auto-classification for record {}", applyResult.recordId(), ex);
          }
        }
        triggerReportAnalysisGeneration(applyResult.recordId(), jobId);
      }
    } catch (JsonProcessingException ex) {
      LOGGER.error("Invalid parse result payload: {}", payload, ex);
    } catch (Exception ex) {
      LOGGER.error("Failed to consume parse result payload: {}", payload, ex);
    }
  }

  private void triggerReportAnalysisGeneration(UUID recordId, UUID jobId) {
    try {
      RecordAnalysisContext context = recordService.fetchRecordAnalysisContext(recordId).orElse(null);
      if (context == null || !isAnalysisContextReady(context)) {
        LOGGER.info("Skipped report analysis generation due to parse context not ready: recordId={} jobId={}", recordId, jobId);
        return;
      }
      ParseJobContext parseContext = parseJobService.parseJobContext(jobId);
      generateRequestPublisher.publish(new GenerateRequestEvent(
          UUID.randomUUID().toString(),
          parseContext.tenantId(),
          recordId.toString(),
          "REPORT_ANALYSIS",
          UUID.randomUUID().toString().replace("-", ""),
          "v1",
          context,
          "report-analysis-" + jobId));
      LOGGER.info("Queued report analysis generation for recordId={} jobId={}", recordId, jobId);
    } catch (Exception ex) {
      LOGGER.warn("Failed to queue report analysis generation for recordId={} jobId={}", recordId, jobId, ex);
    }
  }

  private boolean isAnalysisContextReady(RecordAnalysisContext context) {
    if (!"SUCCESS".equalsIgnoreCase(context.parseStatus())) {
      return false;
    }
    StructuredResultData structuredResult = context.structuredResult();
    if (structuredResult == null || structuredResult.payload() == null) {
      return false;
    }
    return structuredResult.payload().path("fields").isArray() && structuredResult.payload().path("fields").size() > 0;
  }
}
