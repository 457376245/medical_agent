package com.medical.agent.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.medical.agent.application.PatientMemoryService;
import com.medical.agent.domain.dto.ApiResponse;
import com.medical.agent.domain.dto.request.SubmitPatientMemoryEntriesRequest;
import com.medical.agent.domain.dto.response.PatientMemoryEntryListResponseData;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AgentPatientMemoryControllerTest {
  @Mock
  private PatientMemoryService patientMemoryService;

  @Mock
  private InternalAgentApiGuard internalAgentApiGuard;

  @Mock
  private HttpServletRequest httpRequest;

  private AgentPatientMemoryController controller;

  @BeforeEach
  void setUp() {
    controller = new AgentPatientMemoryController(patientMemoryService, internalAgentApiGuard);
  }

  @Test
  void submitMemoriesVerifiesInternalApiKeyBeforeDelegating() {
    SubmitPatientMemoryEntriesRequest request = new SubmitPatientMemoryEntriesRequest(
        "thread-1", "turn-1", null, null, List.of());
    when(patientMemoryService.submitAgentMemories(request))
        .thenReturn(new PatientMemoryEntryListResponseData(List.of()));

    ApiResponse<PatientMemoryEntryListResponseData> response = controller.submitMemories(request, httpRequest);

    assertEquals("created", response.message());
    verify(internalAgentApiGuard).verify(httpRequest);
    verify(patientMemoryService).submitAgentMemories(request);
  }
}
