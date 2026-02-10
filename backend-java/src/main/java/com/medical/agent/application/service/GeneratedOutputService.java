package com.medical.agent.application.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medical.agent.domain.vo.GeneratedOutputSnapshot;
import com.medical.agent.infrastructure.persistence.ScopeConstants;
import com.medical.agent.infrastructure.persistence.entity.GeneratedOutputEntity;
import com.medical.agent.infrastructure.persistence.mapper.GeneratedOutputMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class GeneratedOutputService {
  private final GeneratedOutputMapper generatedOutputMapper;
  private final RecordService recordService;
  private final ObjectMapper objectMapper;

  public GeneratedOutputService(
      GeneratedOutputMapper generatedOutputMapper,
      RecordService recordService,
      ObjectMapper objectMapper) {
    this.generatedOutputMapper = generatedOutputMapper;
    this.recordService = recordService;
    this.objectMapper = objectMapper;
  }

  public int createGeneratedOutput(UUID recordId, String type, String content) {
    return createGeneratedOutputWithMeta(recordId, type, content, "{\"provider\":\"gateway\"}");
  }

  public int createGeneratedOutputWithMeta(UUID recordId, String type, String content, String modelMetaJson) {
    int version = nextVersion(recordId, type);
    generatedOutputMapper.insertWithJsonMeta(
        UUID.randomUUID(),
        ScopeConstants.DEFAULT_TENANT_ID,
        recordService.ensureRecord(recordId),
        type,
        version,
        content,
        modelMetaJson,
        true,
        LocalDateTime.now());
    return version;
  }

  public Optional<GeneratedOutputSnapshot> fetchLatestGeneratedOutput(UUID recordId, String type) {
    List<GeneratedOutputEntity> rows = generatedOutputMapper.selectList(new LambdaQueryWrapper<GeneratedOutputEntity>()
        .eq(GeneratedOutputEntity::getRecordId, recordId)
        .eq(GeneratedOutputEntity::getType, type)
        .orderByDesc(GeneratedOutputEntity::getVersion)
        .last("limit 1"));
    if (rows.isEmpty()) {
      return Optional.empty();
    }

    GeneratedOutputEntity row = rows.get(0);
    JsonNode modelMeta = parsePayload(row.getModelMeta());
    return Optional.of(new GeneratedOutputSnapshot(
        recordId.toString(),
        type,
        row.getVersion() == null ? 0 : row.getVersion(),
        String.valueOf(row.getContent()),
        modelMeta));
  }

  private int nextVersion(UUID recordId, String type) {
    List<GeneratedOutputEntity> latest = generatedOutputMapper.selectList(new LambdaQueryWrapper<GeneratedOutputEntity>()
        .select(GeneratedOutputEntity::getVersion)
        .eq(GeneratedOutputEntity::getRecordId, recordId)
        .eq(GeneratedOutputEntity::getType, type)
        .orderByDesc(GeneratedOutputEntity::getVersion)
        .last("limit 1"));
    if (latest.isEmpty() || latest.get(0).getVersion() == null) {
      return 1;
    }
    return latest.get(0).getVersion() + 1;
  }

  private JsonNode parsePayload(String payloadJson) {
    try {
      return objectMapper.readTree(payloadJson == null ? "{}" : payloadJson);
    } catch (Exception ignored) {
      return objectMapper.createObjectNode();
    }
  }
}
