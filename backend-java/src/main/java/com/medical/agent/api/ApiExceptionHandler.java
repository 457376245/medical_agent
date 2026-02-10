package com.medical.agent.api;

import com.medical.agent.domain.dto.ApiResponse;
import com.medical.agent.domain.dto.response.EmptyData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
  private static final Logger LOGGER = LoggerFactory.getLogger(ApiExceptionHandler.class);

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
