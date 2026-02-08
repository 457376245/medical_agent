package com.medical.agent.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.medical.agent.MedicalAgentApplication;
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
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
      .withDatabaseName("medical_agent_test")
      .withUsername("medical")
      .withPassword("medical");

  @Autowired
  private TestRestTemplate restTemplate;

  @Autowired
  private ParseResultConsumer parseResultConsumer;

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

    ResponseEntity<Map> presignResp = postJson("/api/v1/uploads/presign", Map.of(
        "fileName", "lab.pdf",
        "contentType", "application/pdf",
        "size", 12345));
    assertEquals(HttpStatus.OK, presignResp.getStatusCode());
    Map<String, Object> presignData = dataOf(presignResp.getBody());
    assertTrue(String.valueOf(presignData.get("objectKey")).contains("lab.pdf"));

    ResponseEntity<Map> assetResp = postJson("/api/v1/assets/complete", Map.of(
        "objectKey", presignData.get("objectKey"),
        "checksum", "sha256:abc",
        "recordId", recordId.toString(),
        "size", 12345));
    assertEquals(HttpStatus.OK, assetResp.getStatusCode());
    String assetId = String.valueOf(dataOf(assetResp.getBody()).get("assetId"));
    assertNotNull(assetId);

    HttpHeaders jobHeaders = jsonHeaders();
    jobHeaders.set("Idempotency-Key", "job-" + UUID.randomUUID());
    ResponseEntity<Map> createJobResp = restTemplate.exchange(
        "/api/v1/parse-jobs",
        HttpMethod.POST,
        new HttpEntity<>(Map.of("assetIds", List.of(assetId), "recordId", recordId.toString()), jobHeaders),
        Map.class);
    assertEquals(HttpStatus.OK, createJobResp.getStatusCode());
    String jobId = String.valueOf(dataOf(createJobResp.getBody()).get("jobId"));

    ResponseEntity<Map> status1 = restTemplate.getForEntity("/api/v1/parse-jobs/" + jobId, Map.class);
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
    ResponseEntity<Map> status2 = restTemplate.getForEntity("/api/v1/parse-jobs/" + jobId, Map.class);
    assertEquals("SUCCESS", String.valueOf(dataOf(status2.getBody()).get("status")));
    assertEquals(100, ((Number) dataOf(status2.getBody()).get("progress")).intValue());
    assertNotNull(dataOf(status1.getBody()).get("status"));

    ResponseEntity<Map> recordResp = restTemplate.getForEntity("/api/v1/records/" + recordId, Map.class);
    assertEquals(HttpStatus.OK, recordResp.getStatusCode());
    String summary = String.valueOf(dataOf(recordResp.getBody()).get("summary"));
    assertFalse(summary.isBlank());
  }

  @Test
  void dataRightsExportFlowReturnsDownloadAndDeleteStatus() {
    UUID recordId = UUID.randomUUID();
    postJson("/api/v1/assets/complete", Map.of(
        "objectKey", "uploads/seed/file.pdf",
        "checksum", "sha256:seed",
        "recordId", recordId.toString(),
        "size", 10));

    ResponseEntity<Map> exportReq = postJson("/api/v1/records/" + recordId + "/export-requests", Map.of());
    String exportRequestId = String.valueOf(dataOf(exportReq.getBody()).get("requestId"));

    ResponseEntity<Map> exportStatus = restTemplate.getForEntity(
        "/api/v1/records/" + recordId + "/export-requests/" + exportRequestId,
        Map.class);
    assertEquals("COMPLETED", String.valueOf(dataOf(exportStatus.getBody()).get("status")));

    ResponseEntity<Map> exportDownload = restTemplate.getForEntity(
        "/api/v1/records/" + recordId + "/export-requests/" + exportRequestId + "/download",
        Map.class);
    assertTrue(String.valueOf(dataOf(exportDownload.getBody()).get("downloadUrl")).startsWith("https://"));

    ResponseEntity<Map> deleteReq = postJson("/api/v1/records/" + recordId + "/delete-requests", Map.of());
    String deleteRequestId = String.valueOf(dataOf(deleteReq.getBody()).get("requestId"));
    ResponseEntity<Map> deleteStatus = restTemplate.getForEntity(
        "/api/v1/records/" + recordId + "/delete-requests/" + deleteRequestId,
        Map.class);
    assertEquals("PROCESSING", String.valueOf(dataOf(deleteStatus.getBody()).get("status")));
  }

  @Test
  void parseJobCreationRequiresIdempotencyKey() {
    ResponseEntity<Map> missingHeaderResp = postJson(
        "/api/v1/parse-jobs",
        Map.of("assetIds", List.of(UUID.randomUUID().toString()), "recordId", UUID.randomUUID().toString()));
    assertEquals(HttpStatus.BAD_REQUEST, missingHeaderResp.getStatusCode());
  }

  private ResponseEntity<Map> postJson(String path, Map<String, Object> body) {
    return restTemplate.exchange(path, HttpMethod.POST, new HttpEntity<>(body, jsonHeaders()), Map.class);
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
