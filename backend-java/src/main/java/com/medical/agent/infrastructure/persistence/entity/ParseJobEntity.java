package com.medical.agent.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import java.util.UUID;

@TableName("parse_jobs")
public class ParseJobEntity {
  @TableId("id")
  private UUID id;
  private UUID tenantId;
  private UUID recordId;
  private String status;
  private Integer progress;
  private Integer retryCount;
  private String errorCode;
  private String traceId;
  private String idempotencyKey;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;

  public UUID getId() { return id; }
  public void setId(UUID id) { this.id = id; }
  public UUID getTenantId() { return tenantId; }
  public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
  public UUID getRecordId() { return recordId; }
  public void setRecordId(UUID recordId) { this.recordId = recordId; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public Integer getProgress() { return progress; }
  public void setProgress(Integer progress) { this.progress = progress; }
  public Integer getRetryCount() { return retryCount; }
  public void setRetryCount(Integer retryCount) { this.retryCount = retryCount; }
  public String getErrorCode() { return errorCode; }
  public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
  public String getTraceId() { return traceId; }
  public void setTraceId(String traceId) { this.traceId = traceId; }
  public String getIdempotencyKey() { return idempotencyKey; }
  public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
  public LocalDateTime getCreatedAt() { return createdAt; }
  public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
  public LocalDateTime getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
