package com.medical.agent.domain.dto.request;

public record PresignRequest(String fileName, String contentType, Long size) {}
