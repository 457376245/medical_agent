package com.medical.agent.api;

import com.medical.agent.domain.dto.ApiResponse;
import com.medical.agent.domain.dto.request.LoginRequest;
import com.medical.agent.domain.dto.request.RegisterRequest;
import com.medical.agent.domain.dto.response.EmptyData;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
  @PostMapping("/register")
  public ResponseEntity<ApiResponse<EmptyData>> register(@RequestBody RegisterRequest request) {
    return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).body(new ApiResponse<>(
        "TODO_NOT_IMPLEMENTED",
        "register API is reserved for future multi-tenant implementation",
        RequestIdUtil.newRequestId(),
        new EmptyData()));
  }

  @PostMapping("/login")
  public ResponseEntity<ApiResponse<EmptyData>> login(@RequestBody LoginRequest request) {
    return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).body(new ApiResponse<>(
        "TODO_NOT_IMPLEMENTED",
        "login API is reserved for future multi-tenant implementation",
        RequestIdUtil.newRequestId(),
        new EmptyData()));
  }
}
