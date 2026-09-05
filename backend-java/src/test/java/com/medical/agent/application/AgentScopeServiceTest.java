package com.medical.agent.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.medical.agent.application.context.TenantContextProvider;
import com.medical.agent.infrastructure.persistence.entity.AssetEntity;
import com.medical.agent.infrastructure.persistence.entity.PatientEntity;
import com.medical.agent.infrastructure.persistence.entity.RecordEntity;
import com.medical.agent.infrastructure.persistence.entity.UserEntity;
import com.medical.agent.infrastructure.persistence.mapper.AssetMapper;
import com.medical.agent.infrastructure.persistence.mapper.PatientMapper;
import com.medical.agent.infrastructure.persistence.mapper.RecordMapper;
import com.medical.agent.infrastructure.persistence.mapper.UserMapper;
import com.medical.agent.infrastructure.security.JwtUtil;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AgentScopeServiceTest {
  @Mock JwtUtil jwtUtil;
  @Mock UserMapper userMapper;
  @Mock PatientMapper patientMapper;
  @Mock AssetMapper assetMapper;
  @Mock RecordMapper recordMapper;
  @Mock TenantContextProvider tenantContextProvider;

  private AgentScopeService service;

  @BeforeEach
  void setUp() {
    service = new AgentScopeService(
        jwtUtil, userMapper, patientMapper, assetMapper, recordMapper, tenantContextProvider);
  }

  @Test
  void verifyReturnsOnlyPatientOwnedByJwtUser() {
    UUID tenantId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    UUID patientId = UUID.randomUUID();
    UserEntity user = new UserEntity();
    user.setId(userId);
    PatientEntity patient = new PatientEntity();
    patient.setId(patientId);
    when(jwtUtil.isTokenValid("token")).thenReturn(true);
    when(jwtUtil.extractUserId("token")).thenReturn(userId.toString());
    when(jwtUtil.extractTenantId("token")).thenReturn(tenantId.toString());
    when(userMapper.selectOne(any())).thenReturn(user);
    when(patientMapper.selectOne(any())).thenReturn(patient);

    var result = service.verify("Bearer token", patientId.toString());

    assertEquals(tenantId.toString(), result.tenantId());
    assertEquals(userId.toString(), result.userId());
    assertEquals(patientId.toString(), result.patientId());
  }

  @Test
  void attachmentAuthorizationRequiresAssetTenantAndPatientRecord() {
    UUID tenantId = UUID.randomUUID();
    UUID patientId = UUID.randomUUID();
    UUID allowedRecordId = UUID.randomUUID();
    UUID foreignRecordId = UUID.randomUUID();
    when(tenantContextProvider.currentTenantId()).thenReturn(tenantId);
    when(tenantContextProvider.currentPatientId()).thenReturn(patientId);
    AssetEntity allowed = asset(tenantId, allowedRecordId, "allowed.pdf");
    AssetEntity foreign = asset(tenantId, foreignRecordId, "foreign.pdf");
    when(assetMapper.selectList(any())).thenReturn(List.of(allowed, foreign));
    RecordEntity allowedRecord = new RecordEntity();
    allowedRecord.setId(allowedRecordId);
    when(recordMapper.selectList(any())).thenReturn(List.of(allowedRecord));

    List<String> result = service.authorizeAttachmentKeys(List.of("allowed.pdf", "foreign.pdf"));

    assertEquals(List.of("allowed.pdf"), result);
  }

  private AssetEntity asset(UUID tenantId, UUID recordId, String key) {
    AssetEntity entity = new AssetEntity();
    entity.setId(UUID.randomUUID());
    entity.setTenantId(tenantId);
    entity.setRecordId(recordId);
    entity.setObjectKey(key);
    return entity;
  }
}
