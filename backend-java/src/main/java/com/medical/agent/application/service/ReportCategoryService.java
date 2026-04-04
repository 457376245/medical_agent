package com.medical.agent.application.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.medical.agent.domain.vo.ReportCategorySummary;
import com.medical.agent.application.context.TenantContextProvider;
import com.medical.agent.infrastructure.persistence.entity.RecordEntity;
import com.medical.agent.infrastructure.persistence.entity.ReportCategoryEntity;
import com.medical.agent.infrastructure.persistence.mapper.RecordMapper;
import com.medical.agent.infrastructure.persistence.mapper.ReportCategoryMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
@Tag(name = "报告分类服务", description = "负责报告分类的幂等创建、统计查询与安全删除，保障分类体系稳定")
public class ReportCategoryService {
  private static final int MAX_REPORT_CATEGORY_NAME_LENGTH = 64;

  private final ReportCategoryMapper reportCategoryMapper;
  private final RecordMapper recordMapper;
  private final TenantContextProvider tenantContextProvider;

  public ReportCategoryService(ReportCategoryMapper reportCategoryMapper, RecordMapper recordMapper,
      TenantContextProvider tenantContextProvider) {
    this.reportCategoryMapper = reportCategoryMapper;
    this.recordMapper = recordMapper;
    this.tenantContextProvider = tenantContextProvider;
  }

  @Operation(summary = "创建或复用报告分类", description = "按名称幂等创建分类，若已存在同名分类则直接返回既有ID")
  public UUID createCategory(String name) {
    String normalizedName = normalizeName(name);
    if (normalizedName == null) {
      throw new IllegalArgumentException("Report category name is required");
    }

    ReportCategoryEntity existing = findByName(normalizedName);
    if (existing != null) {
      return existing.getId();
    }

    ReportCategoryEntity entity = new ReportCategoryEntity();
    entity.setId(UUID.randomUUID());
    entity.setTenantId(tenantContextProvider.currentTenantId());
    entity.setUserId(tenantContextProvider.currentUserId());
    entity.setPatientId(tenantContextProvider.currentPatientId());
    entity.setName(normalizedName);
    entity.setCreatedAt(LocalDateTime.now());
    entity.setUpdatedAt(LocalDateTime.now());
    reportCategoryMapper.insert(entity);
    return entity.getId();
  }

  @Operation(summary = "查询报告分类摘要", description = "查询分类列表并聚合每个分类下的记录数量用于前端展示")
  public List<ReportCategorySummary> listCategories() {
    List<ReportCategoryEntity> categories = reportCategoryMapper.selectList(new LambdaQueryWrapper<ReportCategoryEntity>()
        .eq(ReportCategoryEntity::getTenantId, tenantContextProvider.currentTenantId())
        .eq(ReportCategoryEntity::getPatientId, tenantContextProvider.currentPatientId())
        .orderByDesc(ReportCategoryEntity::getUpdatedAt)
        .orderByAsc(ReportCategoryEntity::getName));

    Map<String, Integer> countsBySourceType = loadRecordCountsBySourceType();
    List<ReportCategorySummary> result = new ArrayList<>();
    for (ReportCategoryEntity category : categories) {
      String categoryName = String.valueOf(category.getName());
      result.add(new ReportCategorySummary(
          String.valueOf(category.getId()),
          categoryName,
          String.valueOf(category.getUpdatedAt()),
          countsBySourceType.getOrDefault(categoryName, 0)));
    }
    return result;
  }

  @Operation(summary = "检查报告分类是否存在", description = "在当前租户和用户范围内校验分类ID有效性")
  public boolean categoryExists(UUID reportCategoryId) {
    Long count = reportCategoryMapper.selectCount(new LambdaQueryWrapper<ReportCategoryEntity>()
        .eq(ReportCategoryEntity::getId, reportCategoryId)
        .eq(ReportCategoryEntity::getTenantId, tenantContextProvider.currentTenantId())
        .eq(ReportCategoryEntity::getPatientId, tenantContextProvider.currentPatientId()));
    return count != null && count > 0;
  }

  @Operation(summary = "统计分类下记录数", description = "根据分类ID统计关联记录总数，供删除前冲突检查")
  public int countRecords(UUID reportCategoryId) {
    ReportCategoryEntity category = reportCategoryMapper.selectOne(new LambdaQueryWrapper<ReportCategoryEntity>()
        .eq(ReportCategoryEntity::getId, reportCategoryId)
        .eq(ReportCategoryEntity::getTenantId, tenantContextProvider.currentTenantId())
        .eq(ReportCategoryEntity::getPatientId, tenantContextProvider.currentPatientId())
        .last("limit 1"));
    if (category == null) {
      return 0;
    }

    Long count = recordMapper.selectCount(new LambdaQueryWrapper<RecordEntity>()
        .eq(RecordEntity::getTenantId, tenantContextProvider.currentTenantId())
        .eq(RecordEntity::getPatientId, tenantContextProvider.currentPatientId())
        .eq(RecordEntity::getSourceType, category.getName()));
    return count == null ? 0 : count.intValue();
  }

  @Operation(summary = "无关联记录时删除报告分类", description = "仅在分类无关联记录时执行删除，避免破坏既有记录引用")
  public boolean deleteCategoryIfEmpty(UUID reportCategoryId) {
    ReportCategoryEntity category = reportCategoryMapper.selectOne(new LambdaQueryWrapper<ReportCategoryEntity>()
        .eq(ReportCategoryEntity::getId, reportCategoryId)
        .eq(ReportCategoryEntity::getTenantId, tenantContextProvider.currentTenantId())
        .eq(ReportCategoryEntity::getPatientId, tenantContextProvider.currentPatientId())
        .last("limit 1"));
    if (category == null) {
      return false;
    }

    Long linked = recordMapper.selectCount(new LambdaQueryWrapper<RecordEntity>()
        .eq(RecordEntity::getTenantId, tenantContextProvider.currentTenantId())
        .eq(RecordEntity::getPatientId, tenantContextProvider.currentPatientId())
        .eq(RecordEntity::getSourceType, category.getName()));
    if (linked != null && linked > 0) {
      return false;
    }

    int deleted = reportCategoryMapper.delete(new LambdaQueryWrapper<ReportCategoryEntity>()
        .eq(ReportCategoryEntity::getId, reportCategoryId)
        .eq(ReportCategoryEntity::getTenantId, tenantContextProvider.currentTenantId())
        .eq(ReportCategoryEntity::getPatientId, tenantContextProvider.currentPatientId()));
    return deleted > 0;
  }

  private ReportCategoryEntity findByName(String normalizedName) {
    return reportCategoryMapper.selectOne(new LambdaQueryWrapper<ReportCategoryEntity>()
        .eq(ReportCategoryEntity::getTenantId, tenantContextProvider.currentTenantId())
        .eq(ReportCategoryEntity::getPatientId, tenantContextProvider.currentPatientId())
        .apply("lower(name) = lower({0})", normalizedName)
        .last("limit 1"));
  }

  private Map<String, Integer> loadRecordCountsBySourceType() {
    List<Map<String, Object>> rows = recordMapper.selectMaps(new QueryWrapper<RecordEntity>()
        .select("source_type", "count(*) as total")
        .eq("tenant_id", tenantContextProvider.currentTenantId())
        .eq("patient_id", tenantContextProvider.currentPatientId())
        .isNotNull("source_type")
        .groupBy("source_type"));

    Map<String, Integer> countsBySourceType = new HashMap<>();
    for (Map<String, Object> row : rows) {
      Object sourceType = row.get("source_type");
      if (sourceType == null) {
        continue;
      }
      Object total = row.get("total");
      int count = total instanceof Number number ? number.intValue() : Integer.parseInt(String.valueOf(total));
      countsBySourceType.put(String.valueOf(sourceType), count);
    }
    return countsBySourceType;
  }

  private String normalizeName(String name) {
    if (name == null) {
      return null;
    }
    String normalized = name.trim();
    if (normalized.isEmpty()) {
      return null;
    }
    if (normalized.length() > MAX_REPORT_CATEGORY_NAME_LENGTH) {
      throw new IllegalArgumentException("Report category name is too long");
    }
    return normalized;
  }
}
