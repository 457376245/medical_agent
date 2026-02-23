package com.medical.agent.application.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.medical.agent.domain.vo.ReportCategorySummary;
import com.medical.agent.infrastructure.persistence.ScopeConstants;
import com.medical.agent.infrastructure.persistence.entity.RecordEntity;
import com.medical.agent.infrastructure.persistence.entity.ReportCategoryEntity;
import com.medical.agent.infrastructure.persistence.mapper.RecordMapper;
import com.medical.agent.infrastructure.persistence.mapper.ReportCategoryMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ReportCategoryService {
  private static final int MAX_REPORT_CATEGORY_NAME_LENGTH = 64;

  private final ReportCategoryMapper reportCategoryMapper;
  private final RecordMapper recordMapper;

  public ReportCategoryService(ReportCategoryMapper reportCategoryMapper, RecordMapper recordMapper) {
    this.reportCategoryMapper = reportCategoryMapper;
    this.recordMapper = recordMapper;
  }

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
    entity.setTenantId(ScopeConstants.DEFAULT_TENANT_ID);
    entity.setUserId(ScopeConstants.DEFAULT_USER_ID);
    entity.setName(normalizedName);
    entity.setCreatedAt(LocalDateTime.now());
    entity.setUpdatedAt(LocalDateTime.now());
    reportCategoryMapper.insert(entity);
    return entity.getId();
  }

  public List<ReportCategorySummary> listCategories() {
    List<ReportCategoryEntity> categories = reportCategoryMapper.selectList(new LambdaQueryWrapper<ReportCategoryEntity>()
        .eq(ReportCategoryEntity::getTenantId, ScopeConstants.DEFAULT_TENANT_ID)
        .eq(ReportCategoryEntity::getUserId, ScopeConstants.DEFAULT_USER_ID)
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

  public boolean categoryExists(UUID reportCategoryId) {
    Long count = reportCategoryMapper.selectCount(new LambdaQueryWrapper<ReportCategoryEntity>()
        .eq(ReportCategoryEntity::getId, reportCategoryId)
        .eq(ReportCategoryEntity::getTenantId, ScopeConstants.DEFAULT_TENANT_ID)
        .eq(ReportCategoryEntity::getUserId, ScopeConstants.DEFAULT_USER_ID));
    return count != null && count > 0;
  }

  public int countRecords(UUID reportCategoryId) {
    ReportCategoryEntity category = reportCategoryMapper.selectOne(new LambdaQueryWrapper<ReportCategoryEntity>()
        .eq(ReportCategoryEntity::getId, reportCategoryId)
        .eq(ReportCategoryEntity::getTenantId, ScopeConstants.DEFAULT_TENANT_ID)
        .eq(ReportCategoryEntity::getUserId, ScopeConstants.DEFAULT_USER_ID)
        .last("limit 1"));
    if (category == null) {
      return 0;
    }

    Long count = recordMapper.selectCount(new LambdaQueryWrapper<RecordEntity>()
        .eq(RecordEntity::getTenantId, ScopeConstants.DEFAULT_TENANT_ID)
        .eq(RecordEntity::getUserId, ScopeConstants.DEFAULT_USER_ID)
        .eq(RecordEntity::getSourceType, category.getName()));
    return count == null ? 0 : count.intValue();
  }

  public boolean deleteCategoryIfEmpty(UUID reportCategoryId) {
    ReportCategoryEntity category = reportCategoryMapper.selectOne(new LambdaQueryWrapper<ReportCategoryEntity>()
        .eq(ReportCategoryEntity::getId, reportCategoryId)
        .eq(ReportCategoryEntity::getTenantId, ScopeConstants.DEFAULT_TENANT_ID)
        .eq(ReportCategoryEntity::getUserId, ScopeConstants.DEFAULT_USER_ID)
        .last("limit 1"));
    if (category == null) {
      return false;
    }

    Long linked = recordMapper.selectCount(new LambdaQueryWrapper<RecordEntity>()
        .eq(RecordEntity::getTenantId, ScopeConstants.DEFAULT_TENANT_ID)
        .eq(RecordEntity::getUserId, ScopeConstants.DEFAULT_USER_ID)
        .eq(RecordEntity::getSourceType, category.getName()));
    if (linked != null && linked > 0) {
      return false;
    }

    int deleted = reportCategoryMapper.delete(new LambdaQueryWrapper<ReportCategoryEntity>()
        .eq(ReportCategoryEntity::getId, reportCategoryId)
        .eq(ReportCategoryEntity::getTenantId, ScopeConstants.DEFAULT_TENANT_ID)
        .eq(ReportCategoryEntity::getUserId, ScopeConstants.DEFAULT_USER_ID));
    return deleted > 0;
  }

  private ReportCategoryEntity findByName(String normalizedName) {
    return reportCategoryMapper.selectOne(new LambdaQueryWrapper<ReportCategoryEntity>()
        .eq(ReportCategoryEntity::getTenantId, ScopeConstants.DEFAULT_TENANT_ID)
        .eq(ReportCategoryEntity::getUserId, ScopeConstants.DEFAULT_USER_ID)
        .apply("lower(name) = lower({0})", normalizedName)
        .last("limit 1"));
  }

  private Map<String, Integer> loadRecordCountsBySourceType() {
    List<Map<String, Object>> rows = recordMapper.selectMaps(new QueryWrapper<RecordEntity>()
        .select("source_type", "count(*) as total")
        .eq("tenant_id", ScopeConstants.DEFAULT_TENANT_ID)
        .eq("user_id", ScopeConstants.DEFAULT_USER_ID)
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
