package com.medical.agent.infrastructure.persistence.mapper;

import com.github.yulichang.base.MPJBaseMapper;
import com.medical.agent.infrastructure.persistence.entity.StructuredResultEntity;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

public interface StructuredResultMapper extends MPJBaseMapper<StructuredResultEntity> {
  @Insert("insert into structured_results (id, tenant_id, job_id, record_id, schema_version, payload_json, confidence_score, revision, is_user_edited, created_at, updated_at) values (#{id}, #{tenantId}, #{jobId}, #{recordId}, #{schemaVersion}, cast(#{payloadJson} as jsonb), #{confidenceScore}, #{revision}, #{isUserEdited}, #{createdAt}, #{updatedAt})")
  int insertWithJson(
      @Param("id") UUID id,
      @Param("tenantId") UUID tenantId,
      @Param("jobId") UUID jobId,
      @Param("recordId") UUID recordId,
      @Param("schemaVersion") String schemaVersion,
      @Param("payloadJson") String payloadJson,
      @Param("confidenceScore") BigDecimal confidenceScore,
      @Param("revision") Integer revision,
      @Param("isUserEdited") Boolean isUserEdited,
      @Param("createdAt") LocalDateTime createdAt,
      @Param("updatedAt") LocalDateTime updatedAt);
}
