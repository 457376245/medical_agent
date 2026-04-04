package com.medical.agent.application.context;

import java.util.UUID;

public interface TenantContextProvider {
  UUID currentTenantId();

  UUID currentUserId();

  UUID currentPatientId();
}
