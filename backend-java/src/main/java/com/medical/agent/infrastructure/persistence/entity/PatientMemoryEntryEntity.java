package com.medical.agent.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import java.util.UUID;

@TableName("patient_memory_entries")
public class PatientMemoryEntryEntity {
  @TableId("id")
  private UUID id;
  private UUID tenantId;
  private UUID userId;
  private UUID patientId;
  private UUID diseaseProfileId;
  private UUID recordId;
  private String conversationThreadId;
  private String turnId;
  private String memoryType;
  private String fieldPath;
  private String valueText;
  private String valueJson;
  private String evidenceText;
  private String sourceType;
  private String sourceRef;
  private Double confidence;
  private String riskLevel;
  private String status;
  private String rejectionReason;
  private LocalDateTime confirmedAt;
  private UUID supersedesMemoryId;
  private LocalDateTime validFrom;
  private LocalDateTime validTo;
  private Boolean isCurrent;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;

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
  public UUID getRecordId() { return recordId; }
  public void setRecordId(UUID recordId) { this.recordId = recordId; }
  public String getConversationThreadId() { return conversationThreadId; }
  public void setConversationThreadId(String conversationThreadId) { this.conversationThreadId = conversationThreadId; }
  public String getTurnId() { return turnId; }
  public void setTurnId(String turnId) { this.turnId = turnId; }
  public String getMemoryType() { return memoryType; }
  public void setMemoryType(String memoryType) { this.memoryType = memoryType; }
  public String getFieldPath() { return fieldPath; }
  public void setFieldPath(String fieldPath) { this.fieldPath = fieldPath; }
  public String getValueText() { return valueText; }
  public void setValueText(String valueText) { this.valueText = valueText; }
  public String getValueJson() { return valueJson; }
  public void setValueJson(String valueJson) { this.valueJson = valueJson; }
  public String getEvidenceText() { return evidenceText; }
  public void setEvidenceText(String evidenceText) { this.evidenceText = evidenceText; }
  public String getSourceType() { return sourceType; }
  public void setSourceType(String sourceType) { this.sourceType = sourceType; }
  public String getSourceRef() { return sourceRef; }
  public void setSourceRef(String sourceRef) { this.sourceRef = sourceRef; }
  public Double getConfidence() { return confidence; }
  public void setConfidence(Double confidence) { this.confidence = confidence; }
  public String getRiskLevel() { return riskLevel; }
  public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public String getRejectionReason() { return rejectionReason; }
  public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }
  public LocalDateTime getConfirmedAt() { return confirmedAt; }
  public void setConfirmedAt(LocalDateTime confirmedAt) { this.confirmedAt = confirmedAt; }
  public UUID getSupersedesMemoryId() { return supersedesMemoryId; }
  public void setSupersedesMemoryId(UUID supersedesMemoryId) { this.supersedesMemoryId = supersedesMemoryId; }
  public LocalDateTime getValidFrom() { return validFrom; }
  public void setValidFrom(LocalDateTime validFrom) { this.validFrom = validFrom; }
  public LocalDateTime getValidTo() { return validTo; }
  public void setValidTo(LocalDateTime validTo) { this.validTo = validTo; }
  public Boolean getIsCurrent() { return isCurrent; }
  public void setIsCurrent(Boolean isCurrent) { this.isCurrent = isCurrent; }
  public LocalDateTime getCreatedAt() { return createdAt; }
  public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
  public LocalDateTime getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
