package com.medical.agent.application.context;

import com.medical.agent.infrastructure.persistence.ScopeConstants;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class DefaultTenantContextProvider implements TenantContextProvider {
  @Override
  public UUID currentTenantId() {
    // TODO: replace with authenticated tenant context when multi-tenant auth is implemented.
    return ScopeConstants.DEFAULT_TENANT_ID;
  }

  @Override
  public UUID currentUserId() {
    // TODO: replace with authenticated member context when organization members are implemented.
    return ScopeConstants.DEFAULT_USER_ID;
  }
}
