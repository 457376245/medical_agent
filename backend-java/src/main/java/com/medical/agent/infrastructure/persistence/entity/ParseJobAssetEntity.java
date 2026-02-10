package com.medical.agent.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import java.util.UUID;

@TableName("parse_job_assets")
public class ParseJobAssetEntity {
  @TableId("job_id")
  private UUID jobId;
  private UUID assetId;
  private LocalDateTime createdAt;

  public UUID getJobId() { return jobId; }
  public void setJobId(UUID jobId) { this.jobId = jobId; }
  public UUID getAssetId() { return assetId; }
  public void setAssetId(UUID assetId) { this.assetId = assetId; }
  public LocalDateTime getCreatedAt() { return createdAt; }
  public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
