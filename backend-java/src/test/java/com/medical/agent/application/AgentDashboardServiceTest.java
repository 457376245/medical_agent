package com.medical.agent.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.medical.agent.application.service.RecordService;
import com.medical.agent.domain.vo.DiseaseProfileOverview;
import com.medical.agent.domain.vo.DiseaseProfileRecordSummary;
import com.medical.agent.domain.vo.RecordTrendData;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AgentDashboardServiceTest {

  @Mock
  private DiseaseProfileQueryService diseaseProfileQueryService;

  @Mock
  private PatientCareService patientCareService;

  @Mock
  private RecordService recordService;

  private AgentDashboardService service;

  @BeforeEach
  void setUp() {
    service = new AgentDashboardService(diseaseProfileQueryService, patientCareService, recordService);
  }

  @Test
  void getTrendBySourceTypeUsesLatestRecordForCategory() {
    String profileId = UUID.randomUUID().toString();
    UUID olderLabRecordId = UUID.randomUUID();
    UUID latestLabRecordId = UUID.randomUUID();
    RecordTrendData expected = new RecordTrendData(latestLabRecordId.toString(), "LAB", profileId, 6, List.of());
    when(diseaseProfileQueryService.listProfiles()).thenReturn(List.of(
        new DiseaseProfileOverview(profileId, "高血压", 3, "2026-05-03", latestLabRecordId.toString(), "检验报告", "SUCCESS")));
    when(diseaseProfileQueryService.listProfileRecords(profileId)).thenReturn(
        new DiseaseProfileQueryService.ProfileRecordsResult(
            List.of(
                new DiseaseProfileRecordSummary(UUID.randomUUID().toString(), "影像报告", "2026-05-04", "IMAGING"),
                new DiseaseProfileRecordSummary(latestLabRecordId.toString(), "检验报告", "2026-05-03", "LAB"),
                new DiseaseProfileRecordSummary(olderLabRecordId.toString(), "检验报告", "2026-05-01", "LAB")),
            List.of(),
            0));
    when(recordService.fetchTrend(latestLabRecordId, 6)).thenReturn(expected);

    RecordTrendData actual = service.getTrendBySourceType(profileId, " LAB ", 6);

    assertEquals(expected, actual);
    verify(recordService).fetchTrend(latestLabRecordId, 6);
  }

  @Test
  void getTrendBySourceTypeReturnsEmptyTrendWhenCategoryHasNoRecords() {
    String profileId = UUID.randomUUID().toString();
    when(diseaseProfileQueryService.listProfiles()).thenReturn(List.of(
        new DiseaseProfileOverview(profileId, "高血压", 1, "2026-05-03", UUID.randomUUID().toString(), "检验报告", "SUCCESS")));
    when(diseaseProfileQueryService.listProfileRecords(profileId)).thenReturn(
        new DiseaseProfileQueryService.ProfileRecordsResult(
            List.of(new DiseaseProfileRecordSummary(UUID.randomUUID().toString(), "检验报告", "2026-05-03", "LAB")),
            List.of(),
            0));

    RecordTrendData actual = service.getTrendBySourceType(profileId, "IMAGING", 6);

    assertEquals("", actual.recordId());
    assertEquals("IMAGING", actual.sourceType());
    assertEquals(profileId, actual.diseaseProfileId());
    assertEquals(6, actual.limit());
    assertEquals(List.of(), actual.snapshots());
  }
}
