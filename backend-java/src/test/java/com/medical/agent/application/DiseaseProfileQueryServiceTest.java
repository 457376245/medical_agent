package com.medical.agent.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.medical.agent.application.context.TenantContextProvider;
import com.medical.agent.domain.vo.DiseaseProfileExamNode;
import com.medical.agent.infrastructure.persistence.ScopeConstants;
import com.medical.agent.infrastructure.persistence.entity.ParseJobEntity;
import com.medical.agent.infrastructure.persistence.entity.RecordEntity;
import com.medical.agent.infrastructure.persistence.mapper.DiseaseProfileMapper;
import com.medical.agent.infrastructure.persistence.mapper.ParseJobMapper;
import com.medical.agent.infrastructure.persistence.mapper.RecordMapper;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DiseaseProfileQueryServiceTest {
  @Mock
  private RecordMapper recordMapper;

  @Mock
  private DiseaseProfileMapper diseaseProfileMapper;

  @Mock
  private ParseJobMapper parseJobMapper;

  @Mock
  private TenantContextProvider tenantContextProvider;

  private DiseaseProfileQueryService service;

  @BeforeEach
  void setUp() {
    when(tenantContextProvider.currentTenantId()).thenReturn(ScopeConstants.DEFAULT_TENANT_ID);
    when(tenantContextProvider.currentPatientId()).thenReturn(ScopeConstants.DEFAULT_PATIENT_ID);
    service = new DiseaseProfileQueryService(recordMapper, diseaseProfileMapper, parseJobMapper, tenantContextProvider);
  }

  @Test
  void listProfileRecordsGroupsSuccessfulRecordsWithinThreeDaySpanIntoOneExamNode() {
    UUID profileId = UUID.randomUUID();
    when(recordMapper.selectList(any())).thenReturn(List.of(
        record(profileId, "2026-04-04", "IMAGING"),
        record(profileId, "2026-04-03", "OUTPATIENT"),
        record(profileId, "2026-04-01", "LAB")));
    when(parseJobMapper.selectList(any())).thenReturn(List.of(successJob()));

    DiseaseProfileQueryService.ProfileRecordsResult result = service.listProfileRecords(profileId.toString());

    assertEquals(3, result.records().size());
    assertEquals(1, result.examNodes().size());
    DiseaseProfileExamNode node = result.examNodes().get(0);
    assertEquals("2026-04-01", node.dateRangeStart());
    assertEquals("2026-04-04", node.dateRangeEnd());
    assertEquals("2026-04-01 至 2026-04-04", node.displayDate());
    assertEquals(3, node.records().size());
  }

  @Test
  void listProfileRecordsSplitsExamNodesWhenDateSpanWouldExceedThreeDays() {
    UUID profileId = UUID.randomUUID();
    when(recordMapper.selectList(any())).thenReturn(List.of(
        record(profileId, "2026-04-05", "LAB"),
        record(profileId, "2026-04-01", "IMAGING")));
    when(parseJobMapper.selectList(any())).thenReturn(List.of(successJob()));

    DiseaseProfileQueryService.ProfileRecordsResult result = service.listProfileRecords(profileId.toString());

    assertEquals(2, result.examNodes().size());
    assertEquals("2026-04-05", result.examNodes().get(0).displayDate());
    assertEquals("2026-04-01", result.examNodes().get(1).displayDate());
  }

  @Test
  void listProfileRecordsKeepsSameSourceTypeRecordsInsideExamNode() {
    UUID profileId = UUID.randomUUID();
    when(recordMapper.selectList(any())).thenReturn(List.of(
        record(profileId, "2026-04-03", "LAB"),
        record(profileId, "2026-04-01", "LAB")));
    when(parseJobMapper.selectList(any())).thenReturn(List.of(successJob()));

    DiseaseProfileQueryService.ProfileRecordsResult result = service.listProfileRecords(profileId.toString());

    assertEquals(1, result.examNodes().size());
    assertEquals(2, result.examNodes().get(0).records().size());
    assertEquals("LAB", result.examNodes().get(0).records().get(0).sourceType());
    assertEquals("LAB", result.examNodes().get(0).records().get(1).sourceType());
  }

  @Test
  void listProfileRecordsExcludesParsingRecordsFromExamNodes() {
    UUID profileId = UUID.randomUUID();
    when(recordMapper.selectList(any())).thenReturn(List.of(
        record(profileId, "2026-04-03", "LAB"),
        record(profileId, "2026-04-01", "IMAGING")));
    when(parseJobMapper.selectList(any()))
        .thenReturn(List.of(successJob()))
        .thenReturn(List.of(job("QUEUED")));

    DiseaseProfileQueryService.ProfileRecordsResult result = service.listProfileRecords(profileId.toString());

    assertEquals(1, result.records().size());
    assertEquals(1, result.examNodes().size());
    assertEquals(1, result.examNodes().get(0).records().size());
    assertEquals(1, result.parsingCount());
  }

  private RecordEntity record(UUID profileId, String recordDate, String sourceType) {
    RecordEntity record = new RecordEntity();
    record.setId(UUID.randomUUID());
    record.setDiseaseProfileId(profileId);
    record.setRecordDate(LocalDate.parse(recordDate));
    record.setTitle(sourceType + "-" + recordDate);
    record.setSourceType(sourceType);
    return record;
  }

  private ParseJobEntity successJob() {
    return job("SUCCESS");
  }

  private ParseJobEntity job(String status) {
    ParseJobEntity job = new ParseJobEntity();
    job.setStatus(status);
    return job;
  }
}
