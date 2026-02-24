package com.medical.agent.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "认证", description = "登录与注册接口")
public class AuthController {

  private final JwtUtil jwtUtil;

  public AuthController(JwtUtil jwtUtil) {
    this.jwtUtil = jwtUtil;
  }

  @PostMapping("/register")
  @Operation(
      summary = "用户注册",
      description = "注册用户账号（当前为模拟实现，不返回 token）",
      requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
          required = true,
          description = "注册参数",
          content = @Content(
              mediaType = "application/json",
              examples = @ExampleObject(value = "{\"email\":\"doctor@example.com\",\"password\":\"P@ssw0rd!\",\"displayName\":\"张医生\"}"))))
  @ApiResponses(value = {
      @io.swagger.v3.oas.annotations.responses.ApiResponse(
          responseCode = "200",
          description = "注册成功",
          content = @Content(
              mediaType = "application/json",
              examples = @ExampleObject(value = "{\"code\":\"OK\",\"message\":\"mock registration successful\",\"requestId\":\"req_20260224_001\",\"data\":{}}")))
  })
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
  @Operation(
      summary = "用户登录",
      description = "登录并签发 JWT 访问令牌",
      requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
          required = true,
          description = "登录参数",
          content = @Content(
              mediaType = "application/json",
              examples = @ExampleObject(value = "{\"email\":\"doctor@example.com\",\"password\":\"P@ssw0rd!\"}"))))
  @ApiResponses(value = {
      @io.swagger.v3.oas.annotations.responses.ApiResponse(
          responseCode = "200",
          description = "登录成功",
          content = @Content(
              mediaType = "application/json",
              examples = @ExampleObject(value = "{\"code\":\"OK\",\"message\":\"login successful\",\"requestId\":\"req_20260224_002\",\"data\":{\"token\":\"eyJhbGciOiJIUzI1NiJ9...\",\"type\":\"Bearer\"}}")))
  })
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
