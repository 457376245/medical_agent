package com.medical.agent.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Service
public class ReportAnalysisService {
  private static final String OUTPUT_TYPE = "REPORT_ANALYSIS";
  private static final int MAX_ANALYSIS_CHARACTERS = 300;

  private final PersistenceService persistenceService;
  private final ObjectMapper objectMapper;
  private final RestTemplate restTemplate = new RestTemplate();
  private final String agentBaseUrl;

  public ReportAnalysisService(
      PersistenceService persistenceService,
      ObjectMapper objectMapper,
      @Value("${app.agent.base-url:http://localhost:8090}") String agentBaseUrl) {
    this.persistenceService = persistenceService;
    this.objectMapper = objectMapper;
    this.agentBaseUrl = agentBaseUrl;
  }

  public Map<String, Object> getOrGenerate(UUID recordId) {
    Map<String, Object> cached = persistenceService.fetchLatestGeneratedOutput(recordId, OUTPUT_TYPE);
    if (!cached.isEmpty()) {
      return Map.of(
          "recordId", String.valueOf(cached.get("recordId")),
          "content", String.valueOf(cached.get("content")),
          "cached", true,
          "version", cached.get("version"));
    }

    Map<String, Object> context = persistenceService.fetchRecordAnalysisContext(recordId);
    if (context.isEmpty()) {
      throw new IllegalArgumentException("record not found");
    }
    Map<String, Object> sanitizedContext = sanitizeAnalysisContext(context);
    Map<String, Object> generated = generateFromAgent(recordId, sanitizedContext);
    String content = truncateToMaxCharacters(String.valueOf(generated.getOrDefault("content", "")).trim());
    if (content.isEmpty()) {
      throw new IllegalStateException("analysis content is empty");
    }
    String modelMeta = asJson(generated.getOrDefault("modelMeta", Map.of()));
    int version = persistenceService.createGeneratedOutputWithMeta(recordId, OUTPUT_TYPE, content, modelMeta);
    return Map.of(
        "recordId", recordId.toString(),
        "content", content,
        "cached", false,
        "version", version);
  }

  private Map<String, Object> generateFromAgent(UUID recordId, Map<String, Object> context) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    Map<String, Object> request = Map.of(
        "payload", Map.of(
            "recordId", recordId.toString(),
            "type", OUTPUT_TYPE,
            "traceId", UUID.randomUUID().toString().replace("-", ""),
            "schemaVersion", "v1",
            "analysisContext", context));

    ResponseEntity<Map> response;
    try {
      response = restTemplate.postForEntity(
          agentBaseUrl + "/internal/generate",
          new HttpEntity<>(request, headers),
          Map.class);
    } catch (RestClientException error) {
      throw new IllegalStateException("failed to call report analysis provider", error);
    }

    if (!response.getStatusCode().is2xxSuccessful()) {
      throw new IllegalStateException("analysis provider returned non-2xx status");
    }
    Map<String, Object> body = response.getBody();
    if (body == null) {
      throw new IllegalStateException("analysis provider returned empty body");
    }
    Object dataRaw = body.get("data");
    if (!(dataRaw instanceof Map<?, ?> rawMap)) {
      throw new IllegalStateException("analysis provider returned invalid response");
    }
    @SuppressWarnings("unchecked")
    Map<String, Object> data = (Map<String, Object>) rawMap;
    String status = String.valueOf(data.getOrDefault("status", "FAILED"));
    if (!"SUCCESS".equals(status)) {
      throw new IllegalStateException("analysis provider generation failed");
    }
    return data;
  }

  private Map<String, Object> sanitizeAnalysisContext(Map<String, Object> context) {
    Object structuredResultRaw = context.get("structuredResult");
    if (!(structuredResultRaw instanceof Map<?, ?> structuredResultMapRaw)) {
      return context;
    }
    @SuppressWarnings("unchecked")
    Map<String, Object> structuredResult = (Map<String, Object>) structuredResultMapRaw;

    Object payloadRaw = structuredResult.get("payload");
    if (!(payloadRaw instanceof Map<?, ?> payloadMapRaw)) {
      return context;
    }
    @SuppressWarnings("unchecked")
    Map<String, Object> payload = (Map<String, Object>) payloadMapRaw;
    Object fieldsRaw = payload.get("fields");
    if (!(fieldsRaw instanceof List<?> fieldsList)) {
      return context;
    }
    List<?> compactFields = fieldsList.size() <= 60 ? fieldsList : fieldsList.subList(0, 60);
    Map<String, Object> compactPayload = Map.of("fields", compactFields);
    Map<String, Object> compactResult = Map.of(
        "schemaVersion", String.valueOf(structuredResult.getOrDefault("schemaVersion", "v1")),
        "revision", structuredResult.getOrDefault("revision", 0),
        "payload", compactPayload);
    return Map.of(
        "recordId", context.get("recordId"),
        "title", context.get("title"),
        "recordDate", context.get("recordDate"),
        "sourceType", context.get("sourceType"),
        "diseaseName", context.get("diseaseName"),
        "structuredResult", compactResult);
  }

  private String truncateToMaxCharacters(String content) {
    if (content.length() <= MAX_ANALYSIS_CHARACTERS) {
      return content;
    }
    return content.substring(0, MAX_ANALYSIS_CHARACTERS);
  }

  private String asJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException ignored) {
      return "{}";
    }
  }
}
