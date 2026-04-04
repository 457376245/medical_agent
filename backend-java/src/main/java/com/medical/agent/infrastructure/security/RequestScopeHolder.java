package com.medical.agent.infrastructure.security;

import java.util.UUID;

public final class RequestScopeHolder {
  private static final ThreadLocal<UUID> TENANT_ID = new ThreadLocal<>();
  private static final ThreadLocal<UUID> USER_ID = new ThreadLocal<>();
  private static final ThreadLocal<UUID> PATIENT_ID = new ThreadLocal<>();

  private RequestScopeHolder() {}

  public static void setTenantId(UUID tenantId) { TENANT_ID.set(tenantId); }
  public static UUID getTenantId() { return TENANT_ID.get(); }

  public static void setUserId(UUID userId) { USER_ID.set(userId); }
  public static UUID getUserId() { return USER_ID.get(); }

  public static void setPatientId(UUID patientId) { PATIENT_ID.set(patientId); }
  public static UUID getPatientId() { return PATIENT_ID.get(); }

  public static void clear() {
    TENANT_ID.remove();
    USER_ID.remove();
    PATIENT_ID.remove();
  }
}
