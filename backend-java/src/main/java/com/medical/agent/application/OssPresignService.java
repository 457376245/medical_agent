package com.medical.agent.application;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.HttpMethod;
import com.aliyun.oss.model.GeneratePresignedUrlRequest;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.PutObjectRequest;
import java.io.ByteArrayInputStream;
import java.net.URL;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class OssPresignService {
  @Value("${app.oss.enabled:false}")
  private boolean ossEnabled;

  @Value("${app.oss.endpoint:}")
  private String endpoint;

  @Value("${app.oss.bucket:}")
  private String bucket;

  @Value("${app.oss.access-key-id:}")
  private String accessKeyId;

  @Value("${app.oss.access-key-secret:}")
  private String accessKeySecret;

  @Value("${app.oss.url-expire-seconds:900}")
  private long expireSeconds;

  public Optional<PresignResult> presignPut(String objectKey, String contentType) {
    if (!ossEnabled) {
      return Optional.empty();
    }
    assertConfigured();

    OSS client = new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);
    try {
      Instant expireAt = Instant.now().plusSeconds(expireSeconds);
      GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(bucket, objectKey, HttpMethod.PUT);
      request.setExpiration(Date.from(expireAt));
      if (!isBlank(contentType)) {
        request.setContentType(contentType);
      }
      URL signedUrl = client.generatePresignedUrl(request);
      return Optional.of(new PresignResult(signedUrl.toString(), expireAt));
    } finally {
      client.shutdown();
    }
  }

  public void putObject(String objectKey, String contentType, byte[] body) {
    if (!ossEnabled) {
      throw new IllegalStateException("OSS upload is disabled. Please enable app.oss.enabled first");
    }
    assertConfigured();
    if (isBlank(objectKey)) {
      throw new IllegalArgumentException("objectKey is required");
    }
    if (body == null || body.length == 0) {
      throw new IllegalArgumentException("upload body is empty");
    }

    OSS client = new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);
    try {
      ObjectMetadata metadata = new ObjectMetadata();
      metadata.setContentLength(body.length);
      if (!isBlank(contentType)) {
        metadata.setContentType(contentType);
      }
      PutObjectRequest request = new PutObjectRequest(
          bucket,
          objectKey,
          new ByteArrayInputStream(body),
          metadata);
      client.putObject(request);
    } finally {
      client.shutdown();
    }
  }

  private void assertConfigured() {
    if (isBlank(endpoint) || isBlank(bucket) || isBlank(accessKeyId) || isBlank(accessKeySecret)) {
      throw new IllegalStateException("OSS is enabled but endpoint/bucket/access-key is not fully configured");
    }
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  public record PresignResult(String uploadUrl, Instant expireAt) {}
}
