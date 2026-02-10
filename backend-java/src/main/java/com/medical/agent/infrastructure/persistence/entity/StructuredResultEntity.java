package com.medical.agent.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@TableName("structured_results")
public class StructuredResultEntity {
  @TableId("id")
  private UUID id;
  private UUID tenantId;
  private UUID jobId;
  private UUID recordId;
  private String schemaVersion;
  private String payloadJson;
  private BigDecimal confidenceScore;
  private Integer revision;
  private Boolean isUserEdited;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;

  public UUID getId() { return id; }
  public void setId(UUID id) { this.id = id; }
  public UUID getTenantId() { return tenantId; }
  public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
  public UUID getJobId() { return jobId; }
  public void setJobId(UUID jobId) { this.jobId = jobId; }
  public UUID getRecordId() { return recordId; }
  public void setRecordId(UUID recordId) { this.recordId = recordId; }
  public String getSchemaVersion() { return schemaVersion; }
  public void setSchemaVersion(String schemaVersion) { this.schemaVersion = schemaVersion; }
  public String getPayloadJson() { return payloadJson; }
  public void setPayloadJson(String payloadJson) { this.payloadJson = payloadJson; }
  public BigDecimal getConfidenceScore() { return confidenceScore; }
  public void setConfidenceScore(BigDecimal confidenceScore) { this.confidenceScore = confidenceScore; }
  public Integer getRevision() { return revision; }
  public void setRevision(Integer revision) { this.revision = revision; }
  public Boolean getIsUserEdited() { return isUserEdited; }
  public void setIsUserEdited(Boolean userEdited) { isUserEdited = userEdited; }
  public LocalDateTime getCreatedAt() { return createdAt; }
  public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
  public LocalDateTime getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
