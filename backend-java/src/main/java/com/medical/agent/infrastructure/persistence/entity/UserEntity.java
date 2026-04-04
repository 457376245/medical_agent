package com.medical.agent.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import java.util.UUID;

@TableName("users")
public class UserEntity {
  @TableId("id")
  private UUID id;
  private UUID tenantId;
  private String account;
  private String passwordHash;
  private String displayName;
  private String status;
  private String role;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;

  public UUID getId() { return id; }
  public void setId(UUID id) { this.id = id; }
  public UUID getTenantId() { return tenantId; }
  public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
  public String getAccount() { return account; }
  public void setAccount(String account) { this.account = account; }
  public String getPasswordHash() { return passwordHash; }
  public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
  public String getDisplayName() { return displayName; }
  public void setDisplayName(String displayName) { this.displayName = displayName; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public String getRole() { return role; }
  public void setRole(String role) { this.role = role; }
  public LocalDateTime getCreatedAt() { return createdAt; }
  public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
  public LocalDateTime getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
