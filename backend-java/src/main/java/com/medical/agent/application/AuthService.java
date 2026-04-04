package com.medical.agent.application;

import com.medical.agent.domain.dto.response.LoginResponseData;
import com.medical.agent.infrastructure.persistence.ScopeConstants;
import com.medical.agent.infrastructure.persistence.entity.PatientEntity;
import com.medical.agent.infrastructure.persistence.entity.UserEntity;
import com.medical.agent.infrastructure.persistence.mapper.PatientMapper;
import com.medical.agent.infrastructure.persistence.mapper.UserMapper;
import com.medical.agent.infrastructure.security.JwtUtil;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class AuthService {
  private final UserMapper userMapper;
  private final PatientMapper patientMapper;
  private final PasswordEncoder passwordEncoder;
  private final JwtUtil jwtUtil;

  public AuthService(UserMapper userMapper, PatientMapper patientMapper,
      PasswordEncoder passwordEncoder, JwtUtil jwtUtil) {
    this.userMapper = userMapper;
    this.patientMapper = patientMapper;
    this.passwordEncoder = passwordEncoder;
    this.jwtUtil = jwtUtil;
  }

  public void register(String email, String password, String displayName) {
    UUID tenantId = ScopeConstants.DEFAULT_TENANT_ID;

    UserEntity existing = userMapper.selectOne(
        new LambdaQueryWrapper<UserEntity>()
            .eq(UserEntity::getTenantId, tenantId)
            .eq(UserEntity::getAccount, email));
    if (existing != null) {
      throw new IllegalArgumentException("该邮箱已注册");
    }

    LocalDateTime now = LocalDateTime.now();

    UserEntity user = new UserEntity();
    user.setId(UUID.randomUUID());
    user.setTenantId(tenantId);
    user.setAccount(email);
    user.setPasswordHash(passwordEncoder.encode(password));
    user.setDisplayName(displayName != null ? displayName : email);
    user.setStatus("ACTIVE");
    user.setRole("OWNER");
    user.setCreatedAt(now);
    user.setUpdatedAt(now);
    userMapper.insert(user);

    PatientEntity patient = new PatientEntity();
    patient.setId(UUID.randomUUID());
    patient.setTenantId(tenantId);
    patient.setUserId(user.getId());
    patient.setName("本人");
    patient.setRelationship("本人");
    patient.setIsDefault(true);
    patient.setCreatedAt(now);
    patient.setUpdatedAt(now);
    patientMapper.insert(patient);
  }

  public LoginResponseData login(String email, String password) {
    UUID tenantId = ScopeConstants.DEFAULT_TENANT_ID;

    UserEntity user = userMapper.selectOne(
        new LambdaQueryWrapper<UserEntity>()
            .eq(UserEntity::getTenantId, tenantId)
            .eq(UserEntity::getAccount, email));
    if (user == null) {
      throw new IllegalArgumentException("用户不存在");
    }
    if (user.getPasswordHash() == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
      throw new IllegalArgumentException("密码错误");
    }

    String token = jwtUtil.generateToken(user.getId().toString(), tenantId.toString());

    PatientEntity defaultPatient = patientMapper.selectOne(
        new LambdaQueryWrapper<PatientEntity>()
            .eq(PatientEntity::getUserId, user.getId())
            .eq(PatientEntity::getIsDefault, true));

    return new LoginResponseData(
        token,
        "Bearer",
        user.getId().toString(),
        user.getDisplayName(),
        defaultPatient != null ? defaultPatient.getId().toString() : null);
  }
}
