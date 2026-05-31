package com.medical.agent.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import com.medical.agent.domain.dto.ApiResponse;
import com.medical.agent.domain.dto.response.EmptyData;
import com.medical.agent.domain.exception.BusinessException;
import com.medical.agent.domain.exception.ResourceNotFoundException;

@RestControllerAdvice
public class ApiExceptionHandler {
  private static final Logger LOGGER = LoggerFactory.getLogger(ApiExceptionHandler.class);

  @ExceptionHandler(ResourceNotFoundException.class)
  public ResponseEntity<ApiResponse<EmptyData>> handleResourceNotFoundException(ResourceNotFoundException ex) {
    LOGGER.warn("Resource not found", ex);
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(new ApiResponse<>(
            ex.getCode(),
            ex.getMessage(),
            RequestIdUtil.newRequestId(),
            new EmptyData()));
  }

  @ExceptionHandler(BusinessException.class)
  public ResponseEntity<ApiResponse<EmptyData>> handleBusinessException(BusinessException ex) {
    LOGGER.warn("Business exception", ex);
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(new ApiResponse<>(
            ex.getCode(),
            ex.getMessage(),
            RequestIdUtil.newRequestId(),
        new EmptyData()));
  }

  @ExceptionHandler(ResponseStatusException.class)
  public ResponseEntity<ApiResponse<EmptyData>> handleResponseStatusException(ResponseStatusException ex) {
    LOGGER.warn("Response status exception", ex);
    HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
    if (status == null) {
      status = HttpStatus.BAD_REQUEST;
    }
    return ResponseEntity.status(status)
        .body(new ApiResponse<>(
            status == HttpStatus.UNAUTHORIZED ? "UNAUTHORIZED" : "REQUEST_FAILED",
            ex.getReason() == null ? status.getReasonPhrase() : ex.getReason(),
            RequestIdUtil.newRequestId(),
            new EmptyData()));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiResponse<EmptyData>> handleException(Exception ex) {
    LOGGER.error("Unhandled API exception", ex);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(new ApiResponse<>(
            "BIZ_INTERNAL_ERROR",
            "Internal server error",
            RequestIdUtil.newRequestId(),
            new EmptyData()));
  }
}
