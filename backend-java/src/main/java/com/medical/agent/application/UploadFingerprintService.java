package com.medical.agent.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.springframework.stereotype.Service;

@Service
public class UploadFingerprintService {
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
