package com.medical.agent.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.medical.agent.domain.dto.response.AuthUserResponseData;
import com.medical.agent.infrastructure.persistence.entity.PatientEntity;
import com.medical.agent.infrastructure.persistence.entity.UserEntity;
import com.medical.agent.infrastructure.persistence.mapper.PatientMapper;
import com.medical.agent.infrastructure.persistence.mapper.UserMapper;
import com.medical.agent.infrastructure.security.JwtUtil;
import com.medical.agent.infrastructure.security.RequestScopeHolder;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

  @Mock
  private UserMapper userMapper;

  @Mock
  private PatientMapper patientMapper;

  @Mock
  private PasswordEncoder passwordEncoder;

  @Mock
  private JwtUtil jwtUtil;

  @InjectMocks
  private AuthService authService;

  @AfterEach
  void tearDown() {
    RequestScopeHolder.clear();
  }

  @Test
  void getCurrentUserReturnsUnauthorizedWhenRequestScopeMissing() {
    ResponseStatusException error = assertThrows(
        ResponseStatusException.class,
        () -> authService.getCurrentUser());

    assertEquals(HttpStatus.UNAUTHORIZED, error.getStatusCode());
    assertEquals("未登录或登录已过期", error.getReason());
  }

  @Test
  void getCurrentUserReturnsUnauthorizedWhenUserMissing() {
    UUID userId = UUID.randomUUID();
    RequestScopeHolder.setUserId(userId);
    when(userMapper.selectById(userId)).thenReturn(null);

    ResponseStatusException error = assertThrows(
        ResponseStatusException.class,
        () -> authService.getCurrentUser());

    assertEquals(HttpStatus.UNAUTHORIZED, error.getStatusCode());
    assertEquals("用户不存在", error.getReason());
    verify(userMapper).selectById(userId);
  }

  @Test
  void getCurrentUserReturnsUserProfileWithoutDefaultFallback() {
    UUID userId = UUID.randomUUID();
    UUID patientId = UUID.randomUUID();
    RequestScopeHolder.setUserId(userId);

    UserEntity user = new UserEntity();
    user.setId(userId);
    user.setDisplayName("黄江辉");
    when(userMapper.selectById(userId)).thenReturn(user);

    PatientEntity defaultPatient = new PatientEntity();
    defaultPatient.setId(patientId);
    defaultPatient.setUserId(userId);
    defaultPatient.setIsDefault(true);
    when(patientMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(defaultPatient);

    AuthUserResponseData data = authService.getCurrentUser();

    assertEquals(userId.toString(), data.userId());
    assertEquals("黄江辉", data.displayName());
    assertEquals(patientId.toString(), data.defaultPatientId());
    verify(userMapper).selectById(userId);
    verify(patientMapper).selectOne(any(LambdaQueryWrapper.class));
  }
}
