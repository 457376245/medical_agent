package com.medical.agent.infrastructure.persistence;

import java.util.UUID;

public final class ScopeConstants {
  public static final UUID DEFAULT_TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
  public static final UUID DEFAULT_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
  public static final UUID DEFAULT_PATIENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");

  private ScopeConstants() {}
}
