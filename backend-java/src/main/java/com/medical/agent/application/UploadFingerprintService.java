package com.medical.agent.application;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.springframework.stereotype.Service;

@Service
@Tag(name = "上传指纹服务", description = "为上传对象生成稳定指纹，用于去重、幂等校验与审计关联的领域服务")
public class UploadFingerprintService {
  @Operation(summary = "生成上传指纹", description = "将对象键与校验值组合后进行 SHA-256 计算，生成可复现的业务指纹")
  public String fingerprint(String objectKey, String checksum) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] result = digest.digest((objectKey + ":" + checksum).getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(result);
    } catch (Exception ex) {
      throw new IllegalStateException("Unable to generate fingerprint", ex);
    }
  }
}
