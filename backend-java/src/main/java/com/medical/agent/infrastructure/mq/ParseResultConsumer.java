package com.medical.agent.infrastructure.mq;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medical.agent.application.PersistenceService;
import java.util.Map;
import java.util.UUID;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ParseResultConsumer {
  private static final Logger LOGGER = LoggerFactory.getLogger(ParseResultConsumer.class);
  private final ObjectMapper objectMapper;
  private final PersistenceService persistenceService;

  public ParseResultConsumer(ObjectMapper objectMapper, PersistenceService persistenceService) {
    this.objectMapper = objectMapper;
    this.persistenceService = persistenceService;
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
      persistenceService.applyParseResult(jobId, status, structuredJson, confidence, errorCode);
      LOGGER.info("Applied parse result for jobId={} status={}", jobId, status);
    } catch (JsonProcessingException ex) {
      LOGGER.error("Invalid parse result payload: {}", payload, ex);
    }
  }
}
