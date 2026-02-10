package com.medical.agent.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import java.util.UUID;

@TableName("generated_outputs")
public class GeneratedOutputEntity {
  @TableId("id")
  private UUID id;
  private UUID tenantId;
  private UUID recordId;
  private String type;
  private Integer version;
  private String content;
  private String modelMeta;
  private Boolean requiresConfirmation;
  private LocalDateTime createdAt;

  public UUID getId() { return id; }
  public void setId(UUID id) { this.id = id; }
  public UUID getTenantId() { return tenantId; }
  public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
  public UUID getRecordId() { return recordId; }
  public void setRecordId(UUID recordId) { this.recordId = recordId; }
  public String getType() { return type; }
  public void setType(String type) { this.type = type; }
  public Integer getVersion() { return version; }
  public void setVersion(Integer version) { this.version = version; }
  public String getContent() { return content; }
  public void setContent(String content) { this.content = content; }
  public String getModelMeta() { return modelMeta; }
  public void setModelMeta(String modelMeta) { this.modelMeta = modelMeta; }
  public Boolean getRequiresConfirmation() { return requiresConfirmation; }
  public void setRequiresConfirmation(Boolean requiresConfirmation) { this.requiresConfirmation = requiresConfirmation; }
  public LocalDateTime getCreatedAt() { return createdAt; }
  public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
