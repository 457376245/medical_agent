package com.medical.agent.application.context;

import com.medical.agent.infrastructure.persistence.ScopeConstants;
import com.medical.agent.infrastructure.security.RequestScopeHolder;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class DefaultTenantContextProvider implements TenantContextProvider {
  @Override
  public UUID currentTenantId() {
    UUID tenantId = RequestScopeHolder.getTenantId();
    return tenantId != null ? tenantId : ScopeConstants.DEFAULT_TENANT_ID;
  }

  @Override
  public UUID currentUserId() {
    UUID userId = RequestScopeHolder.getUserId();
    return userId != null ? userId : ScopeConstants.DEFAULT_USER_ID;
  }

  @Override
  public UUID currentPatientId() {
    UUID patientId = RequestScopeHolder.getPatientId();
    return patientId != null ? patientId : ScopeConstants.DEFAULT_PATIENT_ID;
  }
}
