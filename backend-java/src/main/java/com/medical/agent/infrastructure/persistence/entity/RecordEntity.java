package com.medical.agent.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@TableName("records")
public class RecordEntity {
  @TableId("id")
  private UUID id;
  private UUID tenantId;
  private UUID userId;
  private UUID diseaseProfileId;
  private LocalDate recordDate;
  private String title;
  private String sourceType;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;

  public UUID getId() { return id; }
  public void setId(UUID id) { this.id = id; }
  public UUID getTenantId() { return tenantId; }
  public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
  public UUID getUserId() { return userId; }
  public void setUserId(UUID userId) { this.userId = userId; }
  public UUID getDiseaseProfileId() { return diseaseProfileId; }
  public void setDiseaseProfileId(UUID diseaseProfileId) { this.diseaseProfileId = diseaseProfileId; }
  public LocalDate getRecordDate() { return recordDate; }
  public void setRecordDate(LocalDate recordDate) { this.recordDate = recordDate; }
  public String getTitle() { return title; }
  public void setTitle(String title) { this.title = title; }
  public String getSourceType() { return sourceType; }
  public void setSourceType(String sourceType) { this.sourceType = sourceType; }
  public LocalDateTime getCreatedAt() { return createdAt; }
  public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
  public LocalDateTime getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
