package com.medical.agent.application;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.HttpMethod;
import com.aliyun.oss.model.GeneratePresignedUrlRequest;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.PutObjectRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.ByteArrayInputStream;
import java.net.URL;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@Tag(name = "对象存储签名服务", description = "封装对象存储上传、删除与预签名能力，统一处理开关控制与凭证校验")
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

  @Operation(summary = "生成对象存储预签名地址", description = "根据对象键和内容类型生成短时可用的 PUT 预签名 URL，供客户端直传")
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

  @Operation(summary = "上传对象到对象存储", description = "通过服务端凭证直接上传二进制内容，常用于代理上传与兜底场景")
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

  @Operation(summary = "从对象存储删除对象", description = "按对象键删除文件；若存储未启用或对象不存在则安全忽略")
  public void deleteObject(String objectKey) {
    if (!ossEnabled) {
      return;
    }
    assertConfigured();
    if (isBlank(objectKey)) {
      return;
    }

    OSS client = new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);
    try {
      if (client.doesObjectExist(bucket, objectKey)) {
        client.deleteObject(bucket, objectKey);
      }
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
