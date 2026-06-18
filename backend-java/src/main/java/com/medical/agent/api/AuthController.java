package com.medical.agent.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.medical.agent.application.AuthService;
import com.medical.agent.domain.dto.ApiResponse;
import com.medical.agent.domain.dto.request.LoginRequest;
import com.medical.agent.domain.dto.request.RegisterRequest;
import com.medical.agent.domain.dto.response.AuthUserResponseData;
import com.medical.agent.domain.dto.response.EmptyData;
import com.medical.agent.domain.dto.response.LoginResponseData;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "认证", description = "登录与注册接口")
public class AuthController {

  private final AuthService authService;

  public AuthController(AuthService authService) {
    this.authService = authService;
  }

  @GetMapping("/me")
  @Operation(
      summary = "查询当前登录用户",
      description = "根据 Authorization Bearer token 返回当前登录用户信息")
  @ApiResponses(value = {
      @io.swagger.v3.oas.annotations.responses.ApiResponse(
          responseCode = "200",
          description = "查询成功",
          content = @Content(
              mediaType = "application/json",
              examples = @ExampleObject(value = "{\"code\":\"OK\",\"message\":\"查询成功\",\"requestId\":\"...\",\"data\":{\"userId\":\"...\",\"displayName\":\"张三\",\"defaultPatientId\":\"...\"}}"))),
      @io.swagger.v3.oas.annotations.responses.ApiResponse(
          responseCode = "401",
          description = "未登录或 token 无效",
          content = @Content(
              mediaType = "application/json",
              examples = @ExampleObject(value = "{\"code\":\"UNAUTHORIZED\",\"message\":\"未登录或登录已过期\",\"requestId\":\"...\",\"data\":{}}")))
  })
  public ResponseEntity<ApiResponse<AuthUserResponseData>> me() {
    AuthUserResponseData data = authService.getCurrentUser();
    return ResponseEntity.ok(new ApiResponse<>(
        "OK",
        "查询成功",
        RequestIdUtil.newRequestId(),
        data));
  }

  @PostMapping("/register")
  @Operation(
      summary = "用户注册",
      description = "注册用户账号，自动创建默认病人档案（本人）",
      requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
          required = true,
          description = "注册参数",
          content = @Content(
              mediaType = "application/json",
              examples = @ExampleObject(value = "{\"email\":\"user@example.com\",\"password\":\"P@ssw0rd!\",\"displayName\":\"张三\"}"))))
  @ApiResponses(value = {
      @io.swagger.v3.oas.annotations.responses.ApiResponse(
          responseCode = "200",
          description = "注册成功",
          content = @Content(
              mediaType = "application/json",
              examples = @ExampleObject(value = "{\"code\":\"OK\",\"message\":\"注册成功\",\"requestId\":\"...\",\"data\":{}}")))
  })
  public ResponseEntity<ApiResponse<EmptyData>> register(@RequestBody RegisterRequest request) {
    authService.register(request.email(), request.password(), request.displayName());
    return ResponseEntity.ok(new ApiResponse<>(
        "OK",
        "注册成功",
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
              examples = @ExampleObject(value = "{\"email\":\"user@example.com\",\"password\":\"P@ssw0rd!\"}"))))
  @ApiResponses(value = {
      @io.swagger.v3.oas.annotations.responses.ApiResponse(
          responseCode = "200",
          description = "登录成功",
          content = @Content(
              mediaType = "application/json",
              examples = @ExampleObject(value = "{\"code\":\"OK\",\"message\":\"登录成功\",\"requestId\":\"...\",\"data\":{\"token\":\"eyJ...\",\"type\":\"Bearer\",\"userId\":\"...\",\"displayName\":\"张三\",\"defaultPatientId\":\"...\"}}")))
  })
  public ResponseEntity<ApiResponse<LoginResponseData>> login(@RequestBody LoginRequest request) {
    LoginResponseData data = authService.login(request.email(), request.password());
    return ResponseEntity.ok(new ApiResponse<>(
        "OK",
        "登录成功",
        RequestIdUtil.newRequestId(),
        data));
  }
}
