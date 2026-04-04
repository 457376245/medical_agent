package com.medical.agent.application.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medical.agent.domain.vo.GeneratedOutputSnapshot;
import com.medical.agent.application.context.TenantContextProvider;
import com.medical.agent.infrastructure.persistence.entity.GeneratedOutputEntity;
import com.medical.agent.infrastructure.persistence.mapper.GeneratedOutputMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
@Tag(name = "生成内容服务", description = "负责生成内容的版本递增、结果落库与按类型读取，支撑摘要和分析等能力")
public class GeneratedOutputService {
  private final GeneratedOutputMapper generatedOutputMapper;
  private final RecordService recordService;
  private final ObjectMapper objectMapper;
  private final TenantContextProvider tenantContextProvider;

  public GeneratedOutputService(
      GeneratedOutputMapper generatedOutputMapper,
      RecordService recordService,
      ObjectMapper objectMapper,
      TenantContextProvider tenantContextProvider) {
    this.generatedOutputMapper = generatedOutputMapper;
    this.recordService = recordService;
    this.objectMapper = objectMapper;
    this.tenantContextProvider = tenantContextProvider;
  }

  @Operation(summary = "使用默认元数据创建生成内容", description = "创建带默认模型元信息的生成内容记录，并返回新版本号")
  public int createGeneratedOutput(UUID recordId, String type, String content) {
    return createGeneratedOutputWithMeta(recordId, type, content, "{\"provider\":\"gateway\"}");
  }

  @Operation(summary = "使用自定义元数据创建生成内容", description = "创建带指定模型元信息的生成内容记录，并自动分配版本")
  public int createGeneratedOutputWithMeta(UUID recordId, String type, String content, String modelMetaJson) {
    int version = nextVersion(recordId, type);
    generatedOutputMapper.insertWithJsonMeta(
        UUID.randomUUID(),
        tenantContextProvider.currentTenantId(),
        recordService.ensureRecord(recordId),
        type,
        version,
        content,
        modelMetaJson,
        true,
        LocalDateTime.now());
    return version;
  }

  @Operation(summary = "按类型获取最新生成内容", description = "按记录与类型读取最新一版生成内容，常用于缓存命中场景")
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
