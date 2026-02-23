package com.medical.agent.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.medical.agent.domain.dto.ApiResponse;
import com.medical.agent.domain.dto.request.LoginRequest;
import com.medical.agent.domain.dto.request.RegisterRequest;
import com.medical.agent.domain.dto.response.EmptyData;
import com.medical.agent.domain.dto.response.LoginResponseData;
import com.medical.agent.infrastructure.persistence.ScopeConstants;
import com.medical.agent.infrastructure.security.JwtUtil;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

  private final JwtUtil jwtUtil;

  public AuthController(JwtUtil jwtUtil) {
    this.jwtUtil = jwtUtil;
  }

  @PostMapping("/register")
  public ResponseEntity<ApiResponse<EmptyData>> register(@RequestBody RegisterRequest request) {
    // In a real system, you would hash password and store user here
    // For now we simulate success but do not return a token directly on register
    return ResponseEntity.ok(new ApiResponse<>(
        "OK",
        "mock registration successful",
        RequestIdUtil.newRequestId(),
        new EmptyData()));
  }

  @PostMapping("/login")
  public ResponseEntity<ApiResponse<LoginResponseData>> login(@RequestBody LoginRequest request) {
    // In a real system, authenticate via AuthenticationManager using
    // UserDetailsService
    // For now, we bypass DB credential checking and issue a valid JWT for the
    // provided email

    String email = request.email() != null ? request.email() : "mock_user@example.com";
    String token = jwtUtil.generateToken(email, ScopeConstants.DEFAULT_TENANT_ID.toString());

    return ResponseEntity.ok(new ApiResponse<>(
        "OK",
        "login successful",
        RequestIdUtil.newRequestId(),
        new LoginResponseData(token, "Bearer")));
  }
}
