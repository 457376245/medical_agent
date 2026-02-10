package com.medical.agent.domain.dto;

public record ApiResponse<T>(String code, String message, String requestId, T data) {}
