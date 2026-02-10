package com.medical.agent.infrastructure.persistence.mapper;

import com.github.yulichang.base.MPJBaseMapper;
import com.medical.agent.infrastructure.persistence.entity.ParseJobAssetEntity;
import java.time.LocalDateTime;
import java.util.UUID;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

public interface ParseJobAssetMapper extends MPJBaseMapper<ParseJobAssetEntity> {
  @Insert("insert into parse_job_assets (job_id, asset_id, created_at) values (#{jobId}, #{assetId}, #{createdAt}) on conflict (job_id, asset_id) do nothing")
  int insertIgnore(@Param("jobId") UUID jobId, @Param("assetId") UUID assetId, @Param("createdAt") LocalDateTime createdAt);
}
