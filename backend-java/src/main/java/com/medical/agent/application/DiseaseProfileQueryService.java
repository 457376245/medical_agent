package com.medical.agent.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.medical.agent.domain.vo.DiseaseProfileExamNode;
import com.medical.agent.domain.vo.DiseaseProfileOverview;
import com.medical.agent.domain.vo.DiseaseProfileRecordSummary;
import com.medical.agent.application.context.TenantContextProvider;
import com.medical.agent.infrastructure.persistence.entity.DiseaseProfileEntity;
import com.medical.agent.infrastructure.persistence.entity.ParseJobEntity;
import com.medical.agent.infrastructure.persistence.entity.RecordEntity;
import com.medical.agent.infrastructure.persistence.mapper.DiseaseProfileMapper;
import com.medical.agent.infrastructure.persistence.mapper.ParseJobMapper;
import com.medical.agent.infrastructure.persistence.mapper.RecordMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
@Tag(name = "疾病档案查询服务", description = "聚合疾病档案与记录信息，提供列表总览与按档案维度的查询能力")
public class DiseaseProfileQueryService {
  private static final int EXAM_NODE_WINDOW_DAYS = 3;

  private final RecordMapper recordMapper;
  private final DiseaseProfileMapper diseaseProfileMapper;
  private final ParseJobMapper parseJobMapper;
  private final TenantContextProvider tenantContextProvider;

  public DiseaseProfileQueryService(
      RecordMapper recordMapper,
      DiseaseProfileMapper diseaseProfileMapper,
      ParseJobMapper parseJobMapper,
      TenantContextProvider tenantContextProvider) {
    this.recordMapper = recordMapper;
    this.diseaseProfileMapper = diseaseProfileMapper;
    this.parseJobMapper = parseJobMapper;
    this.tenantContextProvider = tenantContextProvider;
  }

  @Operation(summary = "查询疾病档案总览数据", description = "按最新记录时间聚合疾病档案，返回记录数、最新记录与解析状态")
  public List<DiseaseProfileOverview> listProfiles() {
    // 1. Load all disease profiles for this user
    List<DiseaseProfileEntity> allProfiles = diseaseProfileMapper.selectList(
        new LambdaQueryWrapper<DiseaseProfileEntity>()
            .eq(DiseaseProfileEntity::getTenantId, tenantContextProvider.currentTenantId())
            .eq(DiseaseProfileEntity::getPatientId, tenantContextProvider.currentPatientId())
            .orderByDesc(DiseaseProfileEntity::getUpdatedAt));

    // 2. Load all records and group by profileId
    List<RecordEntity> records = recordMapper.selectList(new LambdaQueryWrapper<RecordEntity>()
        .eq(RecordEntity::getTenantId, tenantContextProvider.currentTenantId())
        .eq(RecordEntity::getPatientId, tenantContextProvider.currentPatientId())
        .isNotNull(RecordEntity::getDiseaseProfileId)
        .orderByDesc(RecordEntity::getRecordDate)
        .orderByDesc(RecordEntity::getUpdatedAt)
        .orderByDesc(RecordEntity::getCreatedAt));

    Map<UUID, ProfileAccumulator> grouped = new LinkedHashMap<>();
    for (RecordEntity record : records) {
      UUID profileId = record.getDiseaseProfileId();
      ProfileAccumulator current = grouped.get(profileId);
      if (current == null) {
        grouped.put(profileId, new ProfileAccumulator(record, 1));
      } else {
        current.recordCount += 1;
      }
    }

    // 3. Build result: every profile appears, with or without records
    List<DiseaseProfileOverview> result = new ArrayList<>();
    for (DiseaseProfileEntity profile : allProfiles) {
      UUID profileId = profile.getId();
      String diseaseName = profile.getName() == null || profile.getName().isBlank()
          ? "未分类疾病"
          : profile.getName();

      ProfileAccumulator accumulator = grouped.get(profileId);
      if (accumulator != null) {
        String latestParseStatus = queryLatestParseStatus(accumulator.latestRecord.getId());
        result.add(new DiseaseProfileOverview(
            String.valueOf(profileId),
            diseaseName,
            accumulator.recordCount,
            String.valueOf(accumulator.latestRecord.getRecordDate()),
            String.valueOf(accumulator.latestRecord.getId()),
            accumulator.latestRecord.getTitle() == null ? "未命名报告" : accumulator.latestRecord.getTitle(),
            latestParseStatus));
      } else {
        // Profile with 0 records
        result.add(new DiseaseProfileOverview(
            String.valueOf(profileId),
            diseaseName,
            0,
            null,
            null,
            null,
            "NOT_PARSED"));
      }
    }
    return result;
  }

  @Operation(summary = "按疾病档案查询记录", description = "查询指定疾病档案下的记录清单，支持 unknown 代表未分类疾病，仅返回解析成功的记录")
  public ProfileRecordsResult listProfileRecords(String profileId) {
    LambdaQueryWrapper<RecordEntity> query = new LambdaQueryWrapper<RecordEntity>()
        .eq(RecordEntity::getTenantId, tenantContextProvider.currentTenantId())
        .eq(RecordEntity::getPatientId, tenantContextProvider.currentPatientId())
        .orderByDesc(RecordEntity::getRecordDate);

    if ("unknown".equalsIgnoreCase(profileId)) {
      query.isNull(RecordEntity::getDiseaseProfileId);
    } else {
      UUID targetProfileId;
      try {
        targetProfileId = UUID.fromString(profileId);
      } catch (IllegalArgumentException error) {
        return new ProfileRecordsResult(List.of(), List.of(), 0);
      }
      query.eq(RecordEntity::getDiseaseProfileId, targetProfileId);
    }

    List<RecordEntity> records = recordMapper.selectList(query);
    List<DiseaseProfileRecordSummary> successRecords = new ArrayList<>();
    int parsingCount = 0;

    for (RecordEntity record : records) {
      String parseStatus = queryLatestParseStatus(record.getId());
      if ("SUCCESS".equals(parseStatus)) {
        successRecords.add(new DiseaseProfileRecordSummary(
            String.valueOf(record.getId()),
            record.getTitle() == null ? "未命名报告" : record.getTitle(),
            String.valueOf(record.getRecordDate()),
            String.valueOf(record.getSourceType())));
      } else {
        // Count records that are still being parsed (QUEUED, RETRYING, or no parse job yet)
        parsingCount++;
      }
    }

    return new ProfileRecordsResult(successRecords, buildExamNodes(successRecords), parsingCount);
  }

  public record ProfileRecordsResult(
      List<DiseaseProfileRecordSummary> records,
      List<DiseaseProfileExamNode> examNodes,
      int parsingCount) {}

  private List<DiseaseProfileExamNode> buildExamNodes(List<DiseaseProfileRecordSummary> records) {
    if (records.isEmpty()) {
      return List.of();
    }

    List<DiseaseProfileRecordSummary> sorted = records.stream()
        .sorted(Comparator
            .comparing((DiseaseProfileRecordSummary record) -> parseRecordDate(record.recordDate())).reversed()
            .thenComparing(DiseaseProfileRecordSummary::id))
        .toList();

    List<DiseaseProfileExamNode> nodes = new ArrayList<>();
    List<DiseaseProfileRecordSummary> currentRecords = new ArrayList<>();
    LocalDate currentStart = null;
    LocalDate currentEnd = null;

    for (DiseaseProfileRecordSummary record : sorted) {
      LocalDate recordDate = parseRecordDate(record.recordDate());
      if (currentRecords.isEmpty()) {
        currentRecords.add(record);
        currentStart = recordDate;
        currentEnd = recordDate;
        continue;
      }

      LocalDate nextStart = recordDate.isBefore(currentStart) ? recordDate : currentStart;
      LocalDate nextEnd = recordDate.isAfter(currentEnd) ? recordDate : currentEnd;
      if (ChronoUnit.DAYS.between(nextStart, nextEnd) <= EXAM_NODE_WINDOW_DAYS) {
        currentRecords.add(record);
        currentStart = nextStart;
        currentEnd = nextEnd;
      } else {
        nodes.add(toExamNode(currentStart, currentEnd, currentRecords));
        currentRecords = new ArrayList<>();
        currentRecords.add(record);
        currentStart = recordDate;
        currentEnd = recordDate;
      }
    }

    if (!currentRecords.isEmpty()) {
      nodes.add(toExamNode(currentStart, currentEnd, currentRecords));
    }
    return nodes;
  }

  private DiseaseProfileExamNode toExamNode(
      LocalDate dateRangeStart,
      LocalDate dateRangeEnd,
      List<DiseaseProfileRecordSummary> records) {
    List<DiseaseProfileRecordSummary> nodeRecords = records.stream()
        .sorted(Comparator
            .comparing((DiseaseProfileRecordSummary record) -> parseRecordDate(record.recordDate()))
            .thenComparing(DiseaseProfileRecordSummary::sourceType)
            .thenComparing(DiseaseProfileRecordSummary::id))
        .toList();
    String start = String.valueOf(dateRangeStart);
    String end = String.valueOf(dateRangeEnd);
    String displayDate = start.equals(end) ? start : start + " 至 " + end;
    String examNodeId = start + "_" + end + "_" + nodeRecords.get(0).id();
    return new DiseaseProfileExamNode(examNodeId, start, start, end, displayDate, nodeRecords);
  }

  private LocalDate parseRecordDate(String recordDate) {
    return LocalDate.parse(recordDate);
  }

  @Operation(summary = "按档案ID解析疾病名称", description = "根据档案ID解析展示名称，无法匹配时统一返回未分类疾病")
  public String diseaseNameByProfile(String profileId) {
    if ("unknown".equalsIgnoreCase(profileId)) {
      return "未分类疾病";
    }
    UUID targetProfileId;
    try {
      targetProfileId = UUID.fromString(profileId);
    } catch (IllegalArgumentException error) {
      return "未分类疾病";
    }

    DiseaseProfileEntity profile = diseaseProfileMapper.selectOne(new LambdaQueryWrapper<DiseaseProfileEntity>()
        .eq(DiseaseProfileEntity::getId, targetProfileId)
        .eq(DiseaseProfileEntity::getTenantId, tenantContextProvider.currentTenantId())
        .eq(DiseaseProfileEntity::getPatientId, tenantContextProvider.currentPatientId())
        .last("limit 1"));
    if (profile == null || profile.getName() == null || profile.getName().isBlank()) {
      return "未分类疾病";
    }
    return profile.getName();
  }

  private String queryLatestParseStatus(UUID recordId) {
    List<ParseJobEntity> jobs = parseJobMapper.selectList(new LambdaQueryWrapper<ParseJobEntity>()
        .eq(ParseJobEntity::getRecordId, recordId)
        .orderByDesc(ParseJobEntity::getUpdatedAt)
        .orderByDesc(ParseJobEntity::getCreatedAt)
        .last("limit 1"));
    if (jobs.isEmpty()) {
      return "NOT_PARSED";
    }
    return String.valueOf(jobs.get(0).getStatus());
  }

  private static final class ProfileAccumulator {
    private final RecordEntity latestRecord;
    private int recordCount;

    private ProfileAccumulator(RecordEntity latestRecord, int recordCount) {
      this.latestRecord = latestRecord;
      this.recordCount = recordCount;
    }
  }
}
