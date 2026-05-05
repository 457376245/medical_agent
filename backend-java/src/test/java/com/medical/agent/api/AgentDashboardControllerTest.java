package com.medical.agent.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.medical.agent.application.AgentDashboardService;
import com.medical.agent.domain.dto.ApiResponse;
import com.medical.agent.domain.dto.response.AgentDashboardResponseData;
import com.medical.agent.domain.dto.response.PatientCareRiskOverviewResponseData;
import com.medical.agent.domain.vo.DiseaseProfileOverview;
import com.medical.agent.domain.vo.DiseaseProfileRecordSummary;
import com.medical.agent.domain.vo.RecordTrendData;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AgentDashboardControllerTest {

  @Mock
  private AgentDashboardService agentDashboardService;

  private AgentDashboardController controller;

  @BeforeEach
  void setUp() {
    controller = new AgentDashboardController(agentDashboardService);
  }

  @Test
  void getDashboardReturnsAggregatedPatientAgentData() {
    AgentDashboardResponseData payload = new AgentDashboardResponseData(
        List.of(new DiseaseProfileOverview("profile-1", "高血压", 2, "2026-05-01", "record-1", "门诊检验", "SUCCESS")),
        new DiseaseProfileOverview("profile-1", "高血压", 2, "2026-05-01", "record-1", "门诊检验", "SUCCESS"),
        new DiseaseProfileRecordSummary("record-1", "门诊检验", "2026-05-01", "LAB"),
        List.of(new DiseaseProfileRecordSummary("record-1", "门诊检验", "2026-05-01", "LAB")),
        new PatientCareRiskOverviewResponseData("watch", "持续观察", List.of(), List.of()),
        List.of(),
        List.of(),
        List.of(),
        List.of("稳定血压"),
        List.of(new AgentDashboardResponseData.TrendHighlight("空腹血糖", "6.8", "6.3", "mmol/L", "up", "high", "record-1", "2026-05-01")),
        List.of("LAB"));
    when(agentDashboardService.getDashboard("profile-1")).thenReturn(payload);

    ApiResponse<AgentDashboardResponseData> response = controller.getDashboard("profile-1");

    assertEquals("OK", response.code());
    assertNotNull(response.requestId());
    assertEquals("高血压", response.data().selectedProfile().diseaseName());
    assertEquals(1, response.data().trendHighlights().size());
  }

  @Test
  void getTrendsReturnsCategoryTrendData() {
    RecordTrendData trendData = new RecordTrendData("record-1", "LAB", "profile-1", 6, List.of());
    when(agentDashboardService.getTrendBySourceType("profile-1", "LAB", 6)).thenReturn(trendData);

    ResponseEntity<ApiResponse<?>> response = controller.getTrends("profile-1", " LAB ", 99);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertEquals("OK", response.getBody().code());
    assertEquals(trendData, response.getBody().data());
    verify(agentDashboardService).getTrendBySourceType("profile-1", "LAB", 6);
  }

  @Test
  void getTrendsReturnsBadRequestWhenSourceTypeMissing() {
    ResponseEntity<ApiResponse<?>> response = controller.getTrends("profile-1", " ", 6);

    assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    assertEquals("INVALID_SOURCE_TYPE", response.getBody().code());
  }
}
