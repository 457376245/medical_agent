package com.medical.agent.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import java.util.UUID;

@TableName("patient_care_profiles")
public class PatientCareProfileEntity {
  @TableId("id")
  private UUID id;
  private UUID tenantId;
  private UUID userId;
  private UUID patientId;
  private String diagnosedConditionsJson;
  private String currentMedicationsJson;
  private String allergiesJson;
  private String abnormalBaselineJson;
  private String doctorInstructions;
  private String careGoalsJson;
  private String redFlagNotesJson;
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
  public String getDiagnosedConditionsJson() { return diagnosedConditionsJson; }
  public void setDiagnosedConditionsJson(String diagnosedConditionsJson) { this.diagnosedConditionsJson = diagnosedConditionsJson; }
  public String getCurrentMedicationsJson() { return currentMedicationsJson; }
  public void setCurrentMedicationsJson(String currentMedicationsJson) { this.currentMedicationsJson = currentMedicationsJson; }
  public String getAllergiesJson() { return allergiesJson; }
  public void setAllergiesJson(String allergiesJson) { this.allergiesJson = allergiesJson; }
  public String getAbnormalBaselineJson() { return abnormalBaselineJson; }
  public void setAbnormalBaselineJson(String abnormalBaselineJson) { this.abnormalBaselineJson = abnormalBaselineJson; }
  public String getDoctorInstructions() { return doctorInstructions; }
  public void setDoctorInstructions(String doctorInstructions) { this.doctorInstructions = doctorInstructions; }
  public String getCareGoalsJson() { return careGoalsJson; }
  public void setCareGoalsJson(String careGoalsJson) { this.careGoalsJson = careGoalsJson; }
  public String getRedFlagNotesJson() { return redFlagNotesJson; }
  public void setRedFlagNotesJson(String redFlagNotesJson) { this.redFlagNotesJson = redFlagNotesJson; }
  public LocalDateTime getCreatedAt() { return createdAt; }
  public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
  public LocalDateTime getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
