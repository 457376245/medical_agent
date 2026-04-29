package com.medical.agent.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.medical.agent.MedicalAgentApplication;
import com.medical.agent.application.service.RecordService;
import com.medical.agent.infrastructure.mq.ParseResultConsumer;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
@SpringBootTest(classes = MedicalAgentApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApiIntegrationTest {
  @Container
  @SuppressWarnings("resource")
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
      .withDatabaseName("medical_agent_test")
      .withUsername("medical")
      .withPassword("medical");

  @Autowired
  private TestRestTemplate restTemplate;

  @Autowired
  private ParseResultConsumer parseResultConsumer;

  @Autowired
  private RecordService recordService;

  @MockBean
  private RabbitTemplate rabbitTemplate;

  @DynamicPropertySource
  static void configureDatasource(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
    registry.add("spring.datasource.driver-class-name", postgres::getDriverClassName);
  }

  @Test
  void endToEndParseFlowPersistsAndReturnsRecord() {
    UUID recordId = UUID.randomUUID();

    ResponseEntity<Map<String, Object>> presignResp = postJson("/api/ingestions/presign", Map.of(
        "fileName", "lab.pdf",
        "contentType", "application/pdf",
        "size", 12345));
    assertEquals(HttpStatus.OK, presignResp.getStatusCode());
    Map<String, Object> presignData = dataOf(presignResp.getBody());
    assertTrue(String.valueOf(presignData.get("objectKey")).contains("lab.pdf"));

    ResponseEntity<Map<String, Object>> assetResp = postJson("/api/ingestions/assets", Map.of(
        "objectKey", presignData.get("objectKey"),
        "checksum", "sha256:abc",
        "recordId", recordId.toString(),
        "size", 12345));
    assertEquals(HttpStatus.OK, assetResp.getStatusCode());
    String assetId = String.valueOf(dataOf(assetResp.getBody()).get("assetId"));
    assertNotNull(assetId);

    HttpHeaders jobHeaders = jsonHeaders();
    jobHeaders.set("Idempotency-Key", "job-" + UUID.randomUUID());
    ResponseEntity<Map<String, Object>> createJobResp = restTemplate.exchange(
        "/api/ingestions/parse-jobs",
        HttpMethod.POST,
        new HttpEntity<>(Map.of("assetIds", List.of(assetId), "recordId", recordId.toString()), jobHeaders),
        new ParameterizedTypeReference<Map<String, Object>>() {});
    assertEquals(HttpStatus.OK, createJobResp.getStatusCode());
    String jobId = String.valueOf(dataOf(createJobResp.getBody()).get("jobId"));

    parseResultConsumer.consume(
        "{" +
            "\"jobId\":\"" + jobId + "\"," +
            "\"status\":\"SUCCESS\"," +
            "\"structuredResult\":{\"fields\":[{" +
            "\"name\":\"hemoglobin\"," +
            "\"value\":\"11.2\"," +
            "\"confidence\":0.91," +
            "\"evidence\":{\"sourceFile\":\"uploads/seed/file.pdf\",\"page\":1,\"snippet\":\"Hemoglobin 11.2 g/dL\"}}]}," +
            "\"confidence\":0.91," +
            "\"errors\":[]" +
            "}");

    ResponseEntity<Map<String, Object>> recordResp = restTemplate.exchange("/api/records/" + recordId, HttpMethod.GET, null, new ParameterizedTypeReference<Map<String, Object>>() {});
    assertEquals(HttpStatus.OK, recordResp.getStatusCode());
    String summary = String.valueOf(dataOf(recordResp.getBody()).get("summary"));
    assertFalse(summary.isBlank());
  }

  @Test
  void parseJobCreationRequiresIdempotencyKey() {
    ResponseEntity<Map<String, Object>> missingHeaderResp = postJson(
        "/api/ingestions/parse-jobs",
        Map.of("assetIds", List.of(UUID.randomUUID().toString()), "recordId", UUID.randomUUID().toString()));
    assertEquals(HttpStatus.BAD_REQUEST, missingHeaderResp.getStatusCode());
  }

  @Test
  void parseJobStatusEndpointReturnsQueuedThenSuccess() {
    UUID recordId = UUID.randomUUID();

    ResponseEntity<Map<String, Object>> assetResp = postJson("/api/ingestions/assets", Map.of(
        "objectKey", "uploads/seed/status-check.pdf",
        "checksum", "sha256:status-check",
        "recordId", recordId.toString(),
        "size", 100));
    String assetId = String.valueOf(dataOf(assetResp.getBody()).get("assetId"));

    HttpHeaders jobHeaders = jsonHeaders();
    jobHeaders.set("Idempotency-Key", "status-" + UUID.randomUUID());
    ResponseEntity<Map<String, Object>> createJobResp = restTemplate.exchange(
        "/api/ingestions/parse-jobs",
        HttpMethod.POST,
        new HttpEntity<>(Map.of("assetIds", List.of(assetId), "recordId", recordId.toString()), jobHeaders),
        new ParameterizedTypeReference<Map<String, Object>>() {});
    String jobId = String.valueOf(dataOf(createJobResp.getBody()).get("jobId"));

    ResponseEntity<Map<String, Object>> statusBefore = restTemplate.exchange("/api/ingestions/parse-jobs/" + jobId, HttpMethod.GET, null, new ParameterizedTypeReference<Map<String, Object>>() {});
    assertEquals(HttpStatus.OK, statusBefore.getStatusCode());
    assertEquals("QUEUED", String.valueOf(dataOf(statusBefore.getBody()).get("status")));

    parseResultConsumer.consume(
        "{" +
            "\"jobId\":\"" + jobId + "\"," +
            "\"status\":\"SUCCESS\"," +
            "\"structuredResult\":{\"fields\":[{\"name\":\"glucose\",\"value\":\"5.1\"}]}," +
            "\"confidence\":0.9," +
            "\"errors\":[]" +
            "}");

    ResponseEntity<Map<String, Object>> statusAfter = restTemplate.exchange("/api/ingestions/parse-jobs/" + jobId, HttpMethod.GET, null, new ParameterizedTypeReference<Map<String, Object>>() {});
    assertEquals(HttpStatus.OK, statusAfter.getStatusCode());
    assertEquals("SUCCESS", String.valueOf(dataOf(statusAfter.getBody()).get("status")));
    assertEquals(100, ((Number) dataOf(statusAfter.getBody()).get("progress")).intValue());
  }

  @Test
  void thresholdReferenceRangeIsEnhancedForRecordTrendAndAnalysisContext() {
    UUID recordId = UUID.randomUUID();

    ResponseEntity<Map<String, Object>> assetResp = postJson("/api/ingestions/assets", Map.of(
        "objectKey", "uploads/seed/threshold-range.pdf",
        "checksum", "sha256:threshold-range",
        "recordId", recordId.toString(),
        "size", 256));
    String assetId = String.valueOf(dataOf(assetResp.getBody()).get("assetId"));

    HttpHeaders jobHeaders = jsonHeaders();
    jobHeaders.set("Idempotency-Key", "threshold-" + UUID.randomUUID());
    ResponseEntity<Map<String, Object>> createJobResp = restTemplate.exchange(
        "/api/ingestions/parse-jobs",
        HttpMethod.POST,
        new HttpEntity<>(Map.of("assetIds", List.of(assetId), "recordId", recordId.toString()), jobHeaders),
        new ParameterizedTypeReference<Map<String, Object>>() {});
    String jobId = String.valueOf(dataOf(createJobResp.getBody()).get("jobId"));

    parseResultConsumer.consume(
        "{" +
            "\"jobId\":\"" + jobId + "\"," +
            "\"status\":\"SUCCESS\"," +
            "\"structuredResult\":{\"fields\":[{" +
            "\"name\":\"HBV-DNA\"," +
            "\"value\":\">1.00×10^8 IU/ml\"," +
            "\"referenceRange\":\"最低检测量 50IU/mL\"," +
            "\"confidence\":0.95}]}," +
            "\"confidence\":0.95," +
            "\"errors\":[]" +
            "}");

    ResponseEntity<Map<String, Object>> recordResp = restTemplate.exchange(
        "/api/records/" + recordId,
        HttpMethod.GET,
        null,
        new ParameterizedTypeReference<Map<String, Object>>() {});
    Map<String, Object> recordData = dataOf(recordResp.getBody());
    @SuppressWarnings("unchecked")
    Map<String, Object> structuredResult = (Map<String, Object>) recordData.get("structuredResult");
    @SuppressWarnings("unchecked")
    Map<String, Object> payload = (Map<String, Object>) structuredResult.get("payload");
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> fields = (List<Map<String, Object>>) payload.get("fields");
    assertEquals("threshold", String.valueOf(fields.get(0).get("resultState")));
    assertEquals("threshold", String.valueOf(fields.get(0).get("comparisonType")));
    assertEquals(100000000.0d, ((Number) fields.get(0).get("numericValue")).doubleValue(), 0.000001d);

    ResponseEntity<Map<String, Object>> trendResp = restTemplate.exchange(
        "/api/records/" + recordId + "/trend?limit=6",
        HttpMethod.GET,
        null,
        new ParameterizedTypeReference<Map<String, Object>>() {});
    Map<String, Object> trendData = dataOf(trendResp.getBody());
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> snapshots = (List<Map<String, Object>>) trendData.get("snapshots");
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> trendFields = (List<Map<String, Object>>) snapshots.get(0).get("fields");
    assertEquals("threshold", String.valueOf(trendFields.get(0).get("resultState")));
    assertEquals(50.0d, ((Number) trendFields.get(0).get("referenceLowerBound")).doubleValue(), 0.000001d);

    assertEquals(
        "threshold",
        recordService.fetchRecordAnalysisContext(recordId)
        .orElseThrow()
        .structuredResult()
        .payload()
        .path("fields")
        .get(0)
        .path("resultState")
        .asText());
  }

  @Test
  void labeledMultiReferenceRangeIsEnhancedForRecordTrendAndAnalysisContext() {
    UUID recordId = UUID.randomUUID();

    ResponseEntity<Map<String, Object>> assetResp = postJson("/api/ingestions/assets", Map.of(
        "objectKey", "uploads/seed/labeled-multi-range.pdf",
        "checksum", "sha256:labeled-multi-range",
        "recordId", recordId.toString(),
        "size", 320));
    String assetId = String.valueOf(dataOf(assetResp.getBody()).get("assetId"));

    HttpHeaders jobHeaders = jsonHeaders();
    jobHeaders.set("Idempotency-Key", "labeled-" + UUID.randomUUID());
    ResponseEntity<Map<String, Object>> createJobResp = restTemplate.exchange(
        "/api/ingestions/parse-jobs",
        HttpMethod.POST,
        new HttpEntity<>(Map.of("assetIds", List.of(assetId), "recordId", recordId.toString()), jobHeaders),
        new ParameterizedTypeReference<Map<String, Object>>() {});
    String jobId = String.valueOf(dataOf(createJobResp.getBody()).get("jobId"));

    parseResultConsumer.consume(
        "{" +
            "\"jobId\":\"" + jobId + "\"," +
            "\"status\":\"SUCCESS\"," +
            "\"structuredResult\":{\"fields\":[" +
            "{" +
            "\"name\":\"非高密度脂蛋白胆固醇\"," +
            "\"value\":\"4.17\"," +
            "\"referenceRange\":\"适宜<4.10 mmol/L;增高4.10-4.90;很高>4.90\"," +
            "\"confidence\":0.95}," +
            "{" +
            "\"name\":\"甘油三酯\"," +
            "\"value\":\"2.15\"," +
            "\"referenceRange\":\"适宜<1.70 mmol/L;增高1.70-2.30;很高>2.30\"," +
            "\"confidence\":0.95}," +
            "{" +
            "\"name\":\"低密度脂蛋白胆固醇\"," +
            "\"value\":\"3.69\"," +
            "\"referenceRange\":\"适宜<3.40 mmol/L;增高3.40-4.10;很高>4.10\"," +
            "\"confidence\":0.95}," +
            "{" +
            "\"name\":\"高密度脂蛋白胆固醇\"," +
            "\"value\":\"2.18\"," +
            "\"referenceRange\":\">1.04 mmol/L\"," +
            "\"confidence\":0.95}" +
            "]}," +
            "\"confidence\":0.95," +
            "\"errors\":[]" +
            "}");

    ResponseEntity<Map<String, Object>> recordResp = restTemplate.exchange(
        "/api/records/" + recordId,
        HttpMethod.GET,
        null,
        new ParameterizedTypeReference<Map<String, Object>>() {});
    Map<String, Object> recordData = dataOf(recordResp.getBody());
    @SuppressWarnings("unchecked")
    Map<String, Object> structuredResult = (Map<String, Object>) recordData.get("structuredResult");
    @SuppressWarnings("unchecked")
    Map<String, Object> payload = (Map<String, Object>) structuredResult.get("payload");
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> fields = (List<Map<String, Object>>) payload.get("fields");
    assertEquals("high", String.valueOf(fields.get(0).get("resultState")));
    assertEquals("high", String.valueOf(fields.get(1).get("resultState")));
    assertEquals("high", String.valueOf(fields.get(2).get("resultState")));
    assertEquals("normal", String.valueOf(fields.get(3).get("resultState")));
    assertEquals("upper_bound", String.valueOf(fields.get(0).get("comparisonType")));
    assertEquals(4.10d, ((Number) fields.get(0).get("referenceUpperBound")).doubleValue(), 0.000001d);

    ResponseEntity<Map<String, Object>> trendResp = restTemplate.exchange(
        "/api/records/" + recordId + "/trend?limit=6",
        HttpMethod.GET,
        null,
        new ParameterizedTypeReference<Map<String, Object>>() {});
    Map<String, Object> trendData = dataOf(trendResp.getBody());
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> snapshots = (List<Map<String, Object>>) trendData.get("snapshots");
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> trendFields = (List<Map<String, Object>>) snapshots.get(0).get("fields");
    assertEquals("high", String.valueOf(trendFields.get(0).get("resultState")));
    assertEquals("normal", String.valueOf(trendFields.get(3).get("resultState")));

    assertEquals(
        "high",
        recordService.fetchRecordAnalysisContext(recordId)
        .orElseThrow()
        .structuredResult()
        .payload()
        .path("fields")
        .get(0)
        .path("resultState")
        .asText());
  }

  @Test
  void parseFlowNormalizesIndicatorsForRecordDetail() {
    UUID recordId = UUID.randomUUID();
    String jobId = createParseJob(recordId, "uploads/seed/indicator-normalization.pdf", "sha256:indicator-normalization");

    parseResultConsumer.consume(
        "{" +
            "\"jobId\":\"" + jobId + "\"," +
            "\"status\":\"SUCCESS\"," +
            "\"structuredResult\":{\"fields\":[" +
            "{\"name\":\"谷丙转氨酶\",\"value\":\"17\",\"referenceRange\":\"7-40\",\"confidence\":0.95}," +
            "{\"name\":\"γ-谷氨酰转肽酶（GGT）\",\"value\":\"35\",\"referenceRange\":\"0-50\",\"confidence\":0.95}," +
            "{\"name\":\"丙氨酸氨基转移酶ALT测定\",\"value\":\"18\",\"referenceRange\":\"7-40\",\"confidence\":0.95}," +
            "{\"name\":\"某未知指标\",\"value\":\"120\",\"referenceRange\":\"0-100\",\"confidence\":0.95}" +
            "]}," +
            "\"confidence\":0.95," +
            "\"errors\":[]" +
            "}");

    List<Map<String, Object>> fields = recordFields(recordId);

    Map<String, Object> alt = fieldByName(fields, "谷丙转氨酶");
    assertEquals("ALT", String.valueOf(alt.get("standardCode")));
    assertEquals("肝功能", String.valueOf(alt.get("category")));

    Map<String, Object> ggt = fieldByName(fields, "γ-谷氨酰转肽酶（GGT）");
    assertEquals("GGT", String.valueOf(ggt.get("standardCode")));
    assertEquals("肝功能", String.valueOf(ggt.get("category")));

    Map<String, Object> mixedAlt = fieldByName(fields, "丙氨酸氨基转移酶ALT测定");
    assertEquals("ALT", String.valueOf(mixedAlt.get("standardCode")));

    Map<String, Object> unmapped = fieldByName(fields, "某未知指标");
    assertEquals("high", String.valueOf(unmapped.get("resultState")));
    assertFalse(unmapped.containsKey("standardCode"));
  }

  @Test
  void parseFlowHandlesValidAndInvalidStandardCode() {
    UUID recordId = UUID.randomUUID();
    String jobId = createParseJob(recordId, "uploads/seed/standard-code-fallback.pdf", "sha256:standard-code-fallback");

    parseResultConsumer.consume(
        "{" +
            "\"jobId\":\"" + jobId + "\"," +
            "\"status\":\"SUCCESS\"," +
            "\"structuredResult\":{\"fields\":[" +
            "{\"name\":\"随便写的名字\",\"value\":\"17\",\"referenceRange\":\"7-40\",\"standardCode\":\"ALT\",\"confidence\":0.95}," +
            "{\"name\":\"谷草转氨酶\",\"value\":\"30\",\"referenceRange\":\"0-40\",\"standardCode\":\"NOT_A_REAL_CODE\",\"confidence\":0.95}," +
            "{\"name\":\"某未知指标\",\"value\":\"120\",\"referenceRange\":\"0-100\",\"standardCode\":\"NOT_A_REAL_CODE\",\"confidence\":0.95}" +
            "]}," +
            "\"confidence\":0.95," +
            "\"errors\":[]" +
            "}");

    List<Map<String, Object>> fields = recordFields(recordId);

    Map<String, Object> validCode = fieldByName(fields, "随便写的名字");
    assertEquals("ALT", String.valueOf(validCode.get("standardCode")));
    assertEquals("肝功能", String.valueOf(validCode.get("category")));

    Map<String, Object> fallback = fieldByName(fields, "谷草转氨酶");
    assertEquals("AST", String.valueOf(fallback.get("standardCode")));
    assertEquals("肝功能", String.valueOf(fallback.get("category")));

    Map<String, Object> unmapped = fieldByName(fields, "某未知指标");
    assertFalse(unmapped.containsKey("standardCode"));
  }

  @Test
  void normalizedIndicatorsDriveCombinationAnalysisInRecordDetail() {
    UUID recordId = UUID.randomUUID();
    String jobId = createParseJob(recordId, "uploads/seed/combination-normalization.pdf", "sha256:combination-normalization");

    parseResultConsumer.consume(
        "{" +
            "\"jobId\":\"" + jobId + "\"," +
            "\"status\":\"SUCCESS\"," +
            "\"structuredResult\":{\"fields\":[" +
            "{\"name\":\"谷草转氨酶\",\"value\":\"120\",\"referenceRange\":\"0-40\",\"confidence\":0.95}," +
            "{\"name\":\"谷丙转氨酶\",\"value\":\"50\",\"referenceRange\":\"0-40\",\"confidence\":0.95}," +
            "{\"name\":\"谷氨酰转肽酶\",\"value\":\"80\",\"referenceRange\":\"0-50\",\"confidence\":0.95}" +
            "]}," +
            "\"confidence\":0.95," +
            "\"errors\":[]" +
            "}");

    Map<String, Object> recordData = recordData(recordId);
    List<Map<String, Object>> fields = payloadFields(recordData);
    assertEquals("AST", String.valueOf(fieldByName(fields, "谷草转氨酶").get("standardCode")));
    assertEquals("ALT", String.valueOf(fieldByName(fields, "谷丙转氨酶").get("standardCode")));
    assertEquals("GGT", String.valueOf(fieldByName(fields, "谷氨酰转肽酶").get("standardCode")));

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> combinationAnalysis =
        (List<Map<String, Object>>) recordData.get("combinationAnalysis");
    assertTrue(combinationAnalysis.stream()
        .anyMatch(item -> "LIVER_ALCOHOLIC".equals(String.valueOf(item.get("ruleId")))));
  }

  @Test
  void parseJobStatusEndpointRejectsInvalidJobId() {
    ResponseEntity<Map<String, Object>> response = restTemplate.exchange("/api/ingestions/parse-jobs/not-a-uuid", HttpMethod.GET, null, new ParameterizedTypeReference<Map<String, Object>>() {});
    assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    assertEquals("INVALID_PARSE_JOB_ID", String.valueOf(response.getBody().get("code")));
    assertEquals("not-a-uuid", String.valueOf(dataOf(response.getBody()).get("jobId")));
  }

  @Test
  void parseJobStatusEndpointReturnsNotFoundForMissingJobId() {
    String missingJobId = UUID.randomUUID().toString();
    ResponseEntity<Map<String, Object>> response = restTemplate.exchange("/api/ingestions/parse-jobs/" + missingJobId, HttpMethod.GET, null, new ParameterizedTypeReference<Map<String, Object>>() {});
    assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    assertEquals("NOT_FOUND", String.valueOf(response.getBody().get("code")));
    assertEquals(missingJobId, String.valueOf(dataOf(response.getBody()).get("jobId")));
  }

  @Test
  void deleteReportCategoryRejectsWhenLinkedRecordsExist() {
    String sourceType = "分类" + UUID.randomUUID().toString().substring(0, 8);
    UUID recordId = UUID.randomUUID();

    postJson("/api/ingestions/assets", Map.of(
        "objectKey", "uploads/seed/report-category-delete.pdf",
        "checksum", "sha256:report-category-delete",
        "recordId", recordId.toString(),
        "sourceType", sourceType,
        "size", 10));

    ResponseEntity<Map<String, Object>> categoriesResp = restTemplate.exchange("/api/report-categories", HttpMethod.GET, null, new ParameterizedTypeReference<Map<String, Object>>() {});
    assertEquals(HttpStatus.OK, categoriesResp.getStatusCode());
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> categories = (List<Map<String, Object>>) dataOf(categoriesResp.getBody()).get("categories");
    String categoryId = categories.stream()
        .filter(item -> sourceType.equals(String.valueOf(item.get("name"))))
        .map(item -> String.valueOf(item.get("id")))
        .findFirst()
        .orElseThrow();

    ResponseEntity<Map<String, Object>> deleteResp = restTemplate.exchange(
        "/api/report-categories/" + categoryId,
        HttpMethod.DELETE,
        new HttpEntity<>(jsonHeaders()),
        new ParameterizedTypeReference<Map<String, Object>>() {});
    assertEquals(HttpStatus.CONFLICT, deleteResp.getStatusCode());
    assertEquals("false", String.valueOf(dataOf(deleteResp.getBody()).get("deleted")));
    assertEquals("HAS_ASSOCIATED_RECORDS", String.valueOf(dataOf(deleteResp.getBody()).get("reason")));
  }

  @Test
  void deleteRecordEndpointRemovesRecord() {
    UUID recordId = UUID.randomUUID();
    postJson("/api/ingestions/assets", Map.of(
        "objectKey", "uploads/seed/file-to-delete.pdf",
        "checksum", "sha256:delete",
        "recordId", recordId.toString(),
        "size", 10));

    ResponseEntity<Map<String, Object>> firstDelete = restTemplate.exchange(
        "/api/records/" + recordId,
        HttpMethod.DELETE,
        new HttpEntity<>(jsonHeaders()),
        new ParameterizedTypeReference<Map<String, Object>>() {});
    assertEquals(HttpStatus.OK, firstDelete.getStatusCode());
    assertEquals("true", String.valueOf(dataOf(firstDelete.getBody()).get("deleted")));

    ResponseEntity<Map<String, Object>> secondDelete = restTemplate.exchange(
        "/api/records/" + recordId,
        HttpMethod.DELETE,
        new HttpEntity<>(jsonHeaders()),
        new ParameterizedTypeReference<Map<String, Object>>() {});
    assertEquals(HttpStatus.NOT_FOUND, secondDelete.getStatusCode());
    assertEquals("false", String.valueOf(dataOf(secondDelete.getBody()).get("deleted")));
  }

  @Test
  void deleteDiseaseProfileEndpointRemovesProfileAndRecords() {
    ResponseEntity<Map<String, Object>> createProfileResp = postJson("/api/disease-profiles", Map.of("name", "慢性肾病"));
    assertEquals(HttpStatus.OK, createProfileResp.getStatusCode());
    String diseaseProfileId = String.valueOf(dataOf(createProfileResp.getBody()).get("diseaseProfileId"));
    UUID recordId = UUID.randomUUID();

    postJson("/api/ingestions/assets", Map.of(
        "objectKey", "uploads/seed/disease-delete.pdf",
        "checksum", "sha256:disease-delete",
        "recordId", recordId.toString(),
        "diseaseProfileId", diseaseProfileId,
        "size", 10));

    ResponseEntity<Map<String, Object>> deleteResp = restTemplate.exchange(
        "/api/disease-profiles/" + diseaseProfileId,
        HttpMethod.DELETE,
        new HttpEntity<>(jsonHeaders()),
        new ParameterizedTypeReference<Map<String, Object>>() {});
    assertEquals(HttpStatus.OK, deleteResp.getStatusCode());
    assertEquals("true", String.valueOf(dataOf(deleteResp.getBody()).get("deleted")));

    ResponseEntity<Map<String, Object>> profilesResp = restTemplate.exchange("/api/disease-profiles", HttpMethod.GET, null, new ParameterizedTypeReference<Map<String, Object>>() {});
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> profiles = (List<Map<String, Object>>) dataOf(profilesResp.getBody()).get("profiles");
    boolean exists = profiles.stream()
        .anyMatch(item -> diseaseProfileId.equals(String.valueOf(item.get("id"))));
    assertFalse(exists);
  }

  @Test
  void deleteDiseaseProfileOnlyIfEmptyRejectsWhenLinkedRecordsExist() {
    ResponseEntity<Map<String, Object>> createProfileResp = postJson("/api/disease-profiles", Map.of("name", "糖尿病"));
    assertEquals(HttpStatus.OK, createProfileResp.getStatusCode());
    String diseaseProfileId = String.valueOf(dataOf(createProfileResp.getBody()).get("diseaseProfileId"));
    UUID recordId = UUID.randomUUID();

    postJson("/api/ingestions/assets", Map.of(
        "objectKey", "uploads/seed/disease-cannot-delete.pdf",
        "checksum", "sha256:disease-cannot-delete",
        "recordId", recordId.toString(),
        "diseaseProfileId", diseaseProfileId,
        "size", 10));

    ResponseEntity<Map<String, Object>> deleteResp = restTemplate.exchange(
        "/api/disease-profiles/" + diseaseProfileId + "?onlyIfEmpty=true",
        HttpMethod.DELETE,
        new HttpEntity<>(jsonHeaders()),
        new ParameterizedTypeReference<Map<String, Object>>() {});
    assertEquals(HttpStatus.CONFLICT, deleteResp.getStatusCode());
    assertEquals("false", String.valueOf(dataOf(deleteResp.getBody()).get("deleted")));
    assertEquals("HAS_ASSOCIATED_RECORDS", String.valueOf(dataOf(deleteResp.getBody()).get("reason")));
  }

  @Test
  void updateRecordSourceTypeEndpointUpdatesCategoryAndTitle() {
    ResponseEntity<Map<String, Object>> createProfileResp = postJson("/api/disease-profiles", Map.of("name", "高血压"));
    assertEquals(HttpStatus.OK, createProfileResp.getStatusCode());
    String diseaseProfileId = String.valueOf(dataOf(createProfileResp.getBody()).get("diseaseProfileId"));
    UUID recordId = UUID.randomUUID();

    postJson("/api/ingestions/assets", Map.of(
        "objectKey", "uploads/seed/source-type-update.pdf",
        "checksum", "sha256:source-type-update",
        "recordId", recordId.toString(),
        "diseaseProfileId", diseaseProfileId,
        "reportDate", "2026-02-09",
        "sourceType", "UPLOAD",
        "title", "原始标题",
        "size", 10));

    ResponseEntity<Map<String, Object>> updateResp = restTemplate.exchange(
        "/api/records/" + recordId + "/source-type",
        HttpMethod.PATCH,
        new HttpEntity<>(Map.of("sourceType", "LAB"), jsonHeaders()),
        new ParameterizedTypeReference<Map<String, Object>>() {});
    assertEquals(HttpStatus.OK, updateResp.getStatusCode());
    assertEquals("true", String.valueOf(dataOf(updateResp.getBody()).get("updated")));
    assertEquals("LAB", String.valueOf(dataOf(updateResp.getBody()).get("sourceType")));
    assertTrue(String.valueOf(dataOf(updateResp.getBody()).get("title")).contains("检验报告"));
  }

  private ResponseEntity<Map<String, Object>> postJson(String path, Map<String, Object> body) {
    return restTemplate.exchange(path, HttpMethod.POST, new HttpEntity<>(body, jsonHeaders()), new ParameterizedTypeReference<Map<String, Object>>() {});
  }

  private String createParseJob(UUID recordId, String objectKey, String checksum) {
    ResponseEntity<Map<String, Object>> assetResp = postJson("/api/ingestions/assets", Map.of(
        "objectKey", objectKey,
        "checksum", checksum,
        "recordId", recordId.toString(),
        "size", 100));
    assertEquals(HttpStatus.OK, assetResp.getStatusCode());
    String assetId = String.valueOf(dataOf(assetResp.getBody()).get("assetId"));

    HttpHeaders jobHeaders = jsonHeaders();
    jobHeaders.set("Idempotency-Key", "normalization-" + UUID.randomUUID());
    ResponseEntity<Map<String, Object>> createJobResp = restTemplate.exchange(
        "/api/ingestions/parse-jobs",
        HttpMethod.POST,
        new HttpEntity<>(Map.of("assetIds", List.of(assetId), "recordId", recordId.toString()), jobHeaders),
        new ParameterizedTypeReference<Map<String, Object>>() {});
    assertEquals(HttpStatus.OK, createJobResp.getStatusCode());
    return String.valueOf(dataOf(createJobResp.getBody()).get("jobId"));
  }

  private Map<String, Object> recordData(UUID recordId) {
    ResponseEntity<Map<String, Object>> recordResp = restTemplate.exchange(
        "/api/records/" + recordId,
        HttpMethod.GET,
        null,
        new ParameterizedTypeReference<Map<String, Object>>() {});
    assertEquals(HttpStatus.OK, recordResp.getStatusCode());
    return dataOf(recordResp.getBody());
  }

  private List<Map<String, Object>> recordFields(UUID recordId) {
    return payloadFields(recordData(recordId));
  }

  @SuppressWarnings("unchecked")
  private List<Map<String, Object>> payloadFields(Map<String, Object> recordData) {
    Map<String, Object> structuredResult = (Map<String, Object>) recordData.get("structuredResult");
    Map<String, Object> payload = (Map<String, Object>) structuredResult.get("payload");
    return (List<Map<String, Object>>) payload.get("fields");
  }

  private Map<String, Object> fieldByName(List<Map<String, Object>> fields, String name) {
    return fields.stream()
        .filter(item -> name.equals(String.valueOf(item.get("name"))))
        .findFirst()
        .orElseThrow();
  }

  private HttpHeaders jsonHeaders() {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    return headers;
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> dataOf(Map<String, Object> body) {
    return (Map<String, Object>) body.get("data");
  }
}


