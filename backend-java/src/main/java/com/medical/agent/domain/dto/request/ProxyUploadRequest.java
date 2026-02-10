package com.medical.agent.domain.dto.request;

public record ProxyUploadRequest(String objectKey, String contentType, String base64Data) {}
