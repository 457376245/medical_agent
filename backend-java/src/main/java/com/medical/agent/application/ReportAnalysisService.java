package com.medical.agent.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.medical.agent.application.service.GeneratedOutputService;
import com.medical.agent.application.service.RecordService;
import com.medical.agent.domain.vo.GeneratedOutputSnapshot;
import com.medical.agent.domain.vo.RecordAnalysisContext;
import com.medical.agent.domain.vo.ReportAnalysisResult;
import com.medical.agent.domain.vo.StructuredResultData;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Optional;
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
@Tag(name = "报告分析服务", description = "负责报告分析结果的缓存命中、分析上下文构建与外部分析引擎调用的领域服务")
public class ReportAnalysisService {
  private static final String OUTPUT_TYPE = "REPORT_ANALYSIS";
  private static final int MAX_ANALYSIS_CHARACTERS = 300;

  private final GeneratedOutputService generatedOutputService;
  private final RecordService recordService;
  private final ObjectMapper objectMapper;
  private final RestTemplate restTemplate = new RestTemplate();
  private final String agentBaseUrl;

  public ReportAnalysisService(
      GeneratedOutputService generatedOutputService,
      RecordService recordService,
      ObjectMapper objectMapper,
      @Value("${app.agent.base-url:http://35.208.147.180:8090}") String agentBaseUrl) {
    this.generatedOutputService = generatedOutputService;
    this.recordService = recordService;
    this.objectMapper = objectMapper;
    this.agentBaseUrl = agentBaseUrl;
  }

  @Operation(summary = "获取缓存分析或生成新分析", description = "优先返回已生成的分析结果；若无缓存则基于结构化数据调用分析引擎并落库")
  public ReportAnalysisResult getOrGenerate(UUID recordId) {
    Optional<GeneratedOutputSnapshot> cached = generatedOutputService.fetchLatestGeneratedOutput(recordId, OUTPUT_TYPE);
    if (cached.isPresent()) {
      GeneratedOutputSnapshot snapshot = cached.get();
      return new ReportAnalysisResult(
          snapshot.recordId(),
          snapshot.content(),
          true,
          snapshot.version());
    }

    RecordAnalysisContext context = recordService.fetchRecordAnalysisContext(recordId)
        .orElseThrow(() -> new IllegalArgumentException("record not found"));
    if (!isParseResultReadyForAnalysis(context)) {
      throw new AnalysisNotReadyException("parse result is not ready for analysis");
    }
    RecordAnalysisContext sanitizedContext = sanitizeAnalysisContext(context);
    GeneratedAnalysis generated = generateFromAgent(recordId, sanitizedContext);
    String content = truncateToMaxCharacters(generated.content().trim());
    if (content.isEmpty()) {
      throw new IllegalStateException("analysis content is empty");
    }
    String modelMeta = asJson(generated.modelMeta());
    int version = generatedOutputService.createGeneratedOutputWithMeta(recordId, OUTPUT_TYPE, content, modelMeta);
    return new ReportAnalysisResult(recordId.toString(), content, false, version);
  }

  public static final class AnalysisNotReadyException extends IllegalStateException {
    public AnalysisNotReadyException(String message) {
      super(message);
    }
  }

  private GeneratedAnalysis generateFromAgent(UUID recordId, RecordAnalysisContext context) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    GenerateRequest request = new GenerateRequest(
        new GeneratePayload(
            recordId.toString(),
            OUTPUT_TYPE,
            UUID.randomUUID().toString().replace("-", ""),
            "v1",
            context));

    ResponseEntity<String> response;
    try {
      response = restTemplate.postForEntity(
          agentBaseUrl + "/internal/generate",
          new HttpEntity<>(request, headers),
          String.class);
    } catch (RestClientException error) {
      throw new IllegalStateException("failed to call report analysis provider", error);
    }

    if (!response.getStatusCode().is2xxSuccessful()) {
      throw new IllegalStateException("analysis provider returned non-2xx status");
    }

    String responseBody = response.getBody();
    if (responseBody == null || responseBody.isBlank()) {
      throw new IllegalStateException("analysis provider returned empty body");
    }

    try {
      JsonNode root = objectMapper.readTree(responseBody);
      JsonNode data = root.path("data");
      if (data.isMissingNode() || !data.isObject()) {
        throw new IllegalStateException("analysis provider returned invalid response");
      }
      String status = data.path("status").asText("FAILED");
      if (!"SUCCESS".equals(status)) {
        throw new IllegalStateException("analysis provider generation failed");
      }
      String content = data.path("content").asText("");
      JsonNode modelMeta = data.path("modelMeta");
      return new GeneratedAnalysis(content, modelMeta.isMissingNode() ? objectMapper.createObjectNode() : modelMeta);
    } catch (JsonProcessingException error) {
      throw new IllegalStateException("analysis provider returned malformed JSON", error);
    }
  }

  private RecordAnalysisContext sanitizeAnalysisContext(RecordAnalysisContext context) {
    StructuredResultData structured = context.structuredResult();
    if (structured == null || structured.payload() == null || !structured.payload().isObject()) {
      return context;
    }

    JsonNode fieldsNode = structured.payload().path("fields");
    if (!fieldsNode.isArray()) {
      return context;
    }

    ArrayNode compactFields = objectMapper.createArrayNode();
    int bound = Math.min(fieldsNode.size(), 60);
    for (int i = 0; i < bound; i++) {
      compactFields.add(fieldsNode.get(i));
    }

    ObjectNode compactPayload = ((ObjectNode) structured.payload()).deepCopy();
    compactPayload.set("fields", compactFields);

    StructuredResultData compactStructured = new StructuredResultData(
        structured.schemaVersion(),
        structured.revision(),
        compactPayload);

    return new RecordAnalysisContext(
        context.recordId(),
        context.title(),
        context.recordDate(),
        context.sourceType(),
        context.diseaseName(),
        context.parseStatus(),
        compactStructured,
        context.combinationAnalysis(),
        context.ultrasoundFollowUp());
  }

  private boolean isParseResultReadyForAnalysis(RecordAnalysisContext context) {
    if (!"SUCCESS".equalsIgnoreCase(context.parseStatus())) {
      return false;
    }
    StructuredResultData structured = context.structuredResult();
    if (structured == null || structured.payload() == null) {
      return false;
    }
    JsonNode fields = structured.payload().path("fields");
    return fields.isArray() && fields.size() > 0;
  }

  private String truncateToMaxCharacters(String content) {
    if (content.length() <= MAX_ANALYSIS_CHARACTERS) {
      return content;
    }
    return content.substring(0, MAX_ANALYSIS_CHARACTERS);
  }

  private String asJson(JsonNode value) {
    try {
      return objectMapper.writeValueAsString(value == null ? objectMapper.createObjectNode() : value);
    } catch (JsonProcessingException ignored) {
      return "{}";
    }
  }

  private record GenerateRequest(GeneratePayload payload) {}

  private record GeneratePayload(
      String recordId,
      String type,
      String traceId,
      String schemaVersion,
      RecordAnalysisContext analysisContext) {}

  private record GeneratedAnalysis(String content, JsonNode modelMeta) {}
}
