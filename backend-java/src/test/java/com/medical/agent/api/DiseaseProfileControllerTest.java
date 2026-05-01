package com.medical.agent.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.medical.agent.application.DiseaseProfileQueryService;
import com.medical.agent.application.DiseaseProfileService;
import com.medical.agent.domain.dto.ApiResponse;
import com.medical.agent.domain.dto.request.DeleteDiseaseProfileRecordsRequest;
import com.medical.agent.domain.dto.response.DiseaseProfileRecordsDeleteResponseData;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class DiseaseProfileControllerTest {

  @Mock
  private DiseaseProfileService diseaseProfileService;

  @Mock
  private DiseaseProfileQueryService diseaseProfileQueryService;

  private DiseaseProfileController controller;

  @BeforeEach
  void setUp() {
    controller = new DiseaseProfileController(diseaseProfileService, diseaseProfileQueryService);
  }

  @Test
  void deleteProfileRecordsReturnsBadRequestWhenProfileIdInvalid() {
    ResponseEntity<ApiResponse<?>> response = controller.deleteProfileRecords(
        "invalid",
        new DeleteDiseaseProfileRecordsRequest(List.of(UUID.randomUUID().toString())));

    assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    assertEquals("INVALID_DISEASE_PROFILE_ID", response.getBody().code());
  }

  @Test
  void deleteProfileRecordsReturnsBadRequestWhenRecordIdsMissing() {
    UUID profileId = UUID.randomUUID();

    ResponseEntity<ApiResponse<?>> response = controller.deleteProfileRecords(
        profileId.toString(),
        new DeleteDiseaseProfileRecordsRequest(List.of()));

    assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    assertEquals("INVALID_RECORD_IDS", response.getBody().code());
  }

  @Test
  void deleteProfileRecordsReturnsBadRequestWhenRecordIdInvalid() {
    UUID profileId = UUID.randomUUID();

    ResponseEntity<ApiResponse<?>> response = controller.deleteProfileRecords(
        profileId.toString(),
        new DeleteDiseaseProfileRecordsRequest(List.of("not-a-uuid")));

    assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    assertEquals("INVALID_RECORD_IDS", response.getBody().code());
    DiseaseProfileRecordsDeleteResponseData data =
        (DiseaseProfileRecordsDeleteResponseData) response.getBody().data();
    assertEquals(List.of("not-a-uuid"), data.rejectedRecordIds());
  }

  @Test
  void deleteProfileRecordsReturnsNotFoundWhenRecordOutsideProfile() {
    UUID profileId = UUID.randomUUID();
    UUID recordId = UUID.randomUUID();
    when(diseaseProfileService.deleteProfileRecords(eq(profileId), eq(List.of(recordId)))).thenReturn(
        new DiseaseProfileService.DeleteProfileRecordsResult(
            true,
            List.of(recordId.toString()),
            1,
            0,
            0,
            0));

    ResponseEntity<ApiResponse<?>> response = controller.deleteProfileRecords(
        profileId.toString(),
        new DeleteDiseaseProfileRecordsRequest(List.of(recordId.toString())));

    assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    assertEquals("NOT_FOUND", response.getBody().code());
    DiseaseProfileRecordsDeleteResponseData data =
        (DiseaseProfileRecordsDeleteResponseData) response.getBody().data();
    assertEquals(List.of(recordId.toString()), data.rejectedRecordIds());
  }

  @Test
  void deleteProfileRecordsReturnsSuccessWhenDeleted() {
    UUID profileId = UUID.randomUUID();
    UUID recordId = UUID.randomUUID();
    when(diseaseProfileService.deleteProfileRecords(eq(profileId), eq(List.of(recordId)))).thenReturn(
        new DiseaseProfileService.DeleteProfileRecordsResult(
            true,
            List.of(),
            1,
            1,
            1,
            1));

    ResponseEntity<ApiResponse<?>> response = controller.deleteProfileRecords(
        profileId.toString(),
        new DeleteDiseaseProfileRecordsRequest(List.of(recordId.toString())));

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertEquals("OK", response.getBody().code());
    DiseaseProfileRecordsDeleteResponseData data =
        (DiseaseProfileRecordsDeleteResponseData) response.getBody().data();
    assertTrue(data.deleted());
    assertEquals(1, data.deletedRecordCount());
    verify(diseaseProfileService).deleteProfileRecords(eq(profileId), eq(List.of(recordId)));
  }
}
