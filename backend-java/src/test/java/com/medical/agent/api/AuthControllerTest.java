package com.medical.agent.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.medical.agent.application.AuthService;
import com.medical.agent.domain.dto.ApiResponse;
import com.medical.agent.domain.dto.response.AuthUserResponseData;
import com.medical.agent.infrastructure.security.RequestScopeHolder;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

  @Mock
  private AuthService authService;

  @InjectMocks
  private AuthController authController;

  @AfterEach
  void tearDown() {
    RequestScopeHolder.clear();
  }

  @Test
  void meReturnsUnauthorizedWhenCurrentUserMissing() {
    when(authService.getCurrentUser()).thenThrow(
        new ResponseStatusException(HttpStatus.UNAUTHORIZED, "未登录或登录已过期"));

    ResponseStatusException error = org.junit.jupiter.api.Assertions.assertThrows(
        ResponseStatusException.class,
        () -> authController.me());

    assertEquals(HttpStatus.UNAUTHORIZED, error.getStatusCode());
    verify(authService).getCurrentUser();
  }

  @Test
  void meReturnsCurrentUserWhenAuthenticated() {
    UUID userId = UUID.randomUUID();
    UUID patientId = UUID.randomUUID();
    when(authService.getCurrentUser()).thenReturn(
        new AuthUserResponseData(userId.toString(), "黄江辉", patientId.toString()));

    ResponseEntity<ApiResponse<AuthUserResponseData>> response = authController.me();

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertEquals("OK", response.getBody().code());
    AuthUserResponseData data = response.getBody().data();
    assertEquals(userId.toString(), data.userId());
    assertEquals("黄江辉", data.displayName());
    assertEquals(patientId.toString(), data.defaultPatientId());
    assertNotNull(response.getBody().requestId());
  }
}
