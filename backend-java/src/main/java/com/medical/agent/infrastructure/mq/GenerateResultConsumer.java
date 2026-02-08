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
public class GenerateResultConsumer {
  private static final Logger LOGGER = LoggerFactory.getLogger(GenerateResultConsumer.class);
  private final ObjectMapper objectMapper;
  private final PersistenceService persistenceService;

  public GenerateResultConsumer(ObjectMapper objectMapper, PersistenceService persistenceService) {
    this.objectMapper = objectMapper;
    this.persistenceService = persistenceService;
  }

  @RabbitListener(queues = "agent.generate.result.v1")
  public void consume(String payload) {
    try {
      Map<String, Object> event = objectMapper.readValue(payload, new TypeReference<>() {});
      String status = String.valueOf(event.getOrDefault("status", "FAILED"));
      if (!"SUCCESS".equals(status)) {
        LOGGER.warn("Generate event failed taskId={} errors={} payload={}", event.get("taskId"), event.get("errors"), payload);
        return;
      }
      UUID recordId = UUID.fromString(String.valueOf(event.get("recordId")));
      String type = String.valueOf(event.getOrDefault("type", "SUMMARY"));
      String content = String.valueOf(event.getOrDefault("content", ""));
      String modelMeta = objectMapper.writeValueAsString(event.getOrDefault("modelMeta", Map.of()));
      int version = persistenceService.createGeneratedOutputWithMeta(recordId, type, content, modelMeta);
      LOGGER.info("Persisted generated output recordId={} type={} version={}", recordId, type, version);
    } catch (JsonProcessingException ex) {
      LOGGER.error("Invalid generate result payload: {}", payload, ex);
    }
  }
}
