package com.medical.agent.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.medical.agent.domain.dto.response.AgentScopeResponse;
import com.medical.agent.infrastructure.persistence.entity.PatientEntity;
import com.medical.agent.infrastructure.persistence.entity.UserEntity;
import com.medical.agent.infrastructure.persistence.mapper.PatientMapper;
import com.medical.agent.infrastructure.persistence.mapper.UserMapper;
import com.medical.agent.infrastructure.persistence.mapper.AssetMapper;
import com.medical.agent.infrastructure.persistence.mapper.RecordMapper;
import com.medical.agent.infrastructure.persistence.entity.AssetEntity;
import com.medical.agent.infrastructure.persistence.entity.RecordEntity;
import com.medical.agent.infrastructure.security.JwtUtil;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import com.medical.agent.application.context.TenantContextProvider;

@Service
public class AgentScopeService {
  private final JwtUtil jwtUtil;
  private final UserMapper userMapper;
  private final PatientMapper patientMapper;
  private final AssetMapper assetMapper;
  private final RecordMapper recordMapper;
  private final TenantContextProvider tenantContextProvider;

  public AgentScopeService(
      JwtUtil jwtUtil,
      UserMapper userMapper,
      PatientMapper patientMapper,
      AssetMapper assetMapper,
      RecordMapper recordMapper,
      TenantContextProvider tenantContextProvider) {
    this.jwtUtil = jwtUtil;
    this.userMapper = userMapper;
    this.patientMapper = patientMapper;
    this.assetMapper = assetMapper;
    this.recordMapper = recordMapper;
    this.tenantContextProvider = tenantContextProvider;
  }

  public AgentScopeResponse verify(String authorization, String requestedPatientId) {
    if (authorization == null || !authorization.startsWith("Bearer ")) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "bearer token required");
    }
    try {
      String token = authorization.substring(7).trim();
      if (token.isEmpty() || !jwtUtil.isTokenValid(token)) {
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "bearer token invalid");
      }
      UUID userId = UUID.fromString(jwtUtil.extractUserId(token));
      UUID tenantId = UUID.fromString(jwtUtil.extractTenantId(token));
      UserEntity user = userMapper.selectOne(new LambdaQueryWrapper<UserEntity>()
          .eq(UserEntity::getId, userId)
          .eq(UserEntity::getTenantId, tenantId)
          .eq(UserEntity::getStatus, "ACTIVE")
          .last("limit 1"));
      if (user == null) {
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "user scope invalid");
      }

      LambdaQueryWrapper<PatientEntity> query = new LambdaQueryWrapper<PatientEntity>()
          .eq(PatientEntity::getTenantId, tenantId)
          .eq(PatientEntity::getUserId, userId)
          .last("limit 1");
      if (requestedPatientId != null && !requestedPatientId.isBlank()) {
        query.eq(PatientEntity::getId, UUID.fromString(requestedPatientId.trim()));
      } else {
        query.orderByDesc(PatientEntity::getIsDefault).orderByAsc(PatientEntity::getCreatedAt);
      }
      PatientEntity patient = patientMapper.selectOne(query);
      if (patient == null) {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "patient scope invalid");
      }
      return new AgentScopeResponse(tenantId.toString(), userId.toString(), patient.getId().toString());
    } catch (ResponseStatusException error) {
      throw error;
    } catch (Exception error) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "bearer token invalid");
    }
  }

  public List<String> authorizeAttachmentKeys(List<String> objectKeys) {
    if (objectKeys == null || objectKeys.isEmpty()) {
      return List.of();
    }
    List<AssetEntity> assets = assetMapper.selectList(new LambdaQueryWrapper<AssetEntity>()
        .eq(AssetEntity::getTenantId, tenantContextProvider.currentTenantId())
        .in(AssetEntity::getObjectKey, objectKeys));
    if (assets.isEmpty()) {
      return List.of();
    }
    Set<UUID> recordIds = new HashSet<>(assets.stream().map(AssetEntity::getRecordId).toList());
    Set<UUID> allowedRecordIds = new HashSet<>(recordMapper.selectList(new LambdaQueryWrapper<RecordEntity>()
        .in(RecordEntity::getId, recordIds)
        .eq(RecordEntity::getTenantId, tenantContextProvider.currentTenantId())
        .eq(RecordEntity::getPatientId, tenantContextProvider.currentPatientId()))
        .stream().map(RecordEntity::getId).toList());
    return assets.stream()
        .filter(asset -> allowedRecordIds.contains(asset.getRecordId()))
        .map(AssetEntity::getObjectKey)
        .distinct()
        .toList();
  }
}
