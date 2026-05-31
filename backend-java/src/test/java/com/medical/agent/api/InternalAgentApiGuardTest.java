package com.medical.agent.api;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class InternalAgentApiGuardTest {
  @Test
  void verifyAllowsRequestsWhenApiKeyIsNotConfigured() {
    InternalAgentApiGuard guard = new InternalAgentApiGuard("", "X-Internal-Api-Key");
    HttpServletRequest request = org.mockito.Mockito.mock(HttpServletRequest.class);

    assertDoesNotThrow(() -> guard.verify(request));
  }

  @Test
  void verifyRejectsRequestsWithWrongApiKey() {
    InternalAgentApiGuard guard = new InternalAgentApiGuard("secret", "X-Internal-Api-Key");
    HttpServletRequest request = org.mockito.Mockito.mock(HttpServletRequest.class);
    when(request.getHeader("X-Internal-Api-Key")).thenReturn("wrong");

    assertThrows(ResponseStatusException.class, () -> guard.verify(request));
  }
}
