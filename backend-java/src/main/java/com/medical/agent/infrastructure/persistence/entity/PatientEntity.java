package com.medical.agent.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@TableName("patients")
public class PatientEntity {
  @TableId("id")
  private UUID id;
  private UUID tenantId;
  private UUID userId;
  private String name;
  private String relationship;
  private String gender;
  private LocalDate birthDate;
  private String notes;
  private Boolean isDefault;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;

  public UUID getId() { return id; }
  public void setId(UUID id) { this.id = id; }
  public UUID getTenantId() { return tenantId; }
  public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
  public UUID getUserId() { return userId; }
  public void setUserId(UUID userId) { this.userId = userId; }
  public String getName() { return name; }
  public void setName(String name) { this.name = name; }
  public String getRelationship() { return relationship; }
  public void setRelationship(String relationship) { this.relationship = relationship; }
  public String getGender() { return gender; }
  public void setGender(String gender) { this.gender = gender; }
  public LocalDate getBirthDate() { return birthDate; }
  public void setBirthDate(LocalDate birthDate) { this.birthDate = birthDate; }
  public String getNotes() { return notes; }
  public void setNotes(String notes) { this.notes = notes; }
  public Boolean getIsDefault() { return isDefault; }
  public void setIsDefault(Boolean isDefault) { this.isDefault = isDefault; }
  public LocalDateTime getCreatedAt() { return createdAt; }
  public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
  public LocalDateTime getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
