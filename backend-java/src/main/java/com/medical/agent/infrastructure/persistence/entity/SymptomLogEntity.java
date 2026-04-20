package com.medical.agent.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import java.util.UUID;

@TableName("symptom_logs")
public class SymptomLogEntity {
  @TableId("id")
  private UUID id;
  private UUID tenantId;
  private UUID userId;
  private UUID patientId;
  private UUID diseaseProfileId;
  private String label;
  private String value;
  private String unit;
  private String alertLevel;
  private String source;
  private String notes;
  private LocalDateTime recordedAt;
  private LocalDateTime createdAt;

  public UUID getId() { return id; }
  public void setId(UUID id) { this.id = id; }
  public UUID getTenantId() { return tenantId; }
  public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
  public UUID getUserId() { return userId; }
  public void setUserId(UUID userId) { this.userId = userId; }
  public UUID getPatientId() { return patientId; }
  public void setPatientId(UUID patientId) { this.patientId = patientId; }
  public UUID getDiseaseProfileId() { return diseaseProfileId; }
  public void setDiseaseProfileId(UUID diseaseProfileId) { this.diseaseProfileId = diseaseProfileId; }
  public String getLabel() { return label; }
  public void setLabel(String label) { this.label = label; }
  public String getValue() { return value; }
  public void setValue(String value) { this.value = value; }
  public String getUnit() { return unit; }
  public void setUnit(String unit) { this.unit = unit; }
  public String getAlertLevel() { return alertLevel; }
  public void setAlertLevel(String alertLevel) { this.alertLevel = alertLevel; }
  public String getSource() { return source; }
  public void setSource(String source) { this.source = source; }
  public String getNotes() { return notes; }
  public void setNotes(String notes) { this.notes = notes; }
  public LocalDateTime getRecordedAt() { return recordedAt; }
  public void setRecordedAt(LocalDateTime recordedAt) { this.recordedAt = recordedAt; }
  public LocalDateTime getCreatedAt() { return createdAt; }
  public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
