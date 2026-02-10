package com.medical.agent.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import java.util.UUID;

@TableName("data_rights_requests")
public class DataRightsRequestEntity {
  @TableId("id")
  private UUID id;
  private UUID tenantId;
  private UUID userId;
  private UUID recordId;
  private String requestType;
  private String status;
  private String downloadUrl;
  private LocalDateTime expireAt;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;

  public UUID getId() { return id; }
  public void setId(UUID id) { this.id = id; }
  public UUID getTenantId() { return tenantId; }
  public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
  public UUID getUserId() { return userId; }
  public void setUserId(UUID userId) { this.userId = userId; }
  public UUID getRecordId() { return recordId; }
  public void setRecordId(UUID recordId) { this.recordId = recordId; }
  public String getRequestType() { return requestType; }
  public void setRequestType(String requestType) { this.requestType = requestType; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public String getDownloadUrl() { return downloadUrl; }
  public void setDownloadUrl(String downloadUrl) { this.downloadUrl = downloadUrl; }
  public LocalDateTime getExpireAt() { return expireAt; }
  public void setExpireAt(LocalDateTime expireAt) { this.expireAt = expireAt; }
  public LocalDateTime getCreatedAt() { return createdAt; }
  public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
  public LocalDateTime getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
