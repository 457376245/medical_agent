package com.medical.agent.infrastructure.persistence.mapper;

import com.github.yulichang.base.MPJBaseMapper;
import com.medical.agent.infrastructure.persistence.entity.GeneratedOutputEntity;
import java.time.LocalDateTime;
import java.util.UUID;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

public interface GeneratedOutputMapper extends MPJBaseMapper<GeneratedOutputEntity> {
  @Insert("insert into generated_outputs (id, tenant_id, record_id, type, version, content, model_meta, requires_confirmation, created_at) values (#{id}, #{tenantId}, #{recordId}, #{type}, #{version}, #{content}, cast(#{modelMeta} as jsonb), #{requiresConfirmation}, #{createdAt})")
  int insertWithJsonMeta(
      @Param("id") UUID id,
      @Param("tenantId") UUID tenantId,
      @Param("recordId") UUID recordId,
      @Param("type") String type,
      @Param("version") Integer version,
      @Param("content") String content,
      @Param("modelMeta") String modelMeta,
      @Param("requiresConfirmation") Boolean requiresConfirmation,
      @Param("createdAt") LocalDateTime createdAt);
}
