package com.medical.agent.api;

import com.medical.agent.domain.dto.ApiResponse;
import com.medical.agent.domain.dto.request.MemberCreateRequest;
import com.medical.agent.domain.dto.response.MemberCreateResponseData;
import com.medical.agent.domain.dto.response.MemberListResponseData;
import com.medical.agent.domain.dto.response.MemberRefResponseData;
import com.medical.agent.domain.dto.response.TenantInfoResponseData;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tenants/current")
@Tag(name = "租户", description = "租户与成员管理接口")
public class TenantController {
  @GetMapping
  @Operation(summary = "获取当前租户", description = "返回当前租户信息（当前为预留接口）")
  @ApiResponses(value = {
      @io.swagger.v3.oas.annotations.responses.ApiResponse(
          responseCode = "501",
          description = "接口预留中",
          content = @Content(mediaType = "application/json", examples = @ExampleObject(value =
              "{\"code\":\"TODO_NOT_IMPLEMENTED\",\"message\":\"tenant profile API is reserved for future multi-tenant implementation\",\"requestId\":\"req_20260224_201\",\"data\":{\"tenantId\":null,\"tenantName\":null,\"status\":\"TODO\"}}")))
  })
  public ResponseEntity<ApiResponse<TenantInfoResponseData>> currentTenant() {
    return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).body(new ApiResponse<>(
        "TODO_NOT_IMPLEMENTED",
        "tenant profile API is reserved for future multi-tenant implementation",
        RequestIdUtil.newRequestId(),
        new TenantInfoResponseData(null, null, "TODO")));
  }

  @GetMapping("/members")
  @Operation(summary = "查询租户成员", description = "返回当前租户成员列表（当前为预留接口）")
  @ApiResponses(value = {
      @io.swagger.v3.oas.annotations.responses.ApiResponse(
          responseCode = "501",
          description = "接口预留中",
          content = @Content(mediaType = "application/json", examples = @ExampleObject(value =
              "{\"code\":\"TODO_NOT_IMPLEMENTED\",\"message\":\"member listing API is reserved for future multi-tenant implementation\",\"requestId\":\"req_20260224_202\",\"data\":{\"members\":[]}}")))
  })
  public ResponseEntity<ApiResponse<MemberListResponseData>> listMembers() {
    return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).body(new ApiResponse<>(
        "TODO_NOT_IMPLEMENTED",
        "member listing API is reserved for future multi-tenant implementation",
        RequestIdUtil.newRequestId(),
        new MemberListResponseData(List.of())));
  }

  @PostMapping("/members")
  @Operation(
      summary = "创建租户成员",
      description = "创建当前租户成员（当前为预留接口）",
      requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
          required = true,
          description = "成员创建参数",
          content = @Content(mediaType = "application/json", examples = @ExampleObject(value = "{\"email\":\"nurse@example.com\",\"displayName\":\"李护士\",\"role\":\"EDITOR\"}"))))
  @ApiResponses(value = {
      @io.swagger.v3.oas.annotations.responses.ApiResponse(
          responseCode = "501",
          description = "接口预留中",
          content = @Content(mediaType = "application/json", examples = @ExampleObject(value =
              "{\"code\":\"TODO_NOT_IMPLEMENTED\",\"message\":\"member creation API is reserved for future multi-tenant implementation\",\"requestId\":\"req_20260224_203\",\"data\":{\"memberId\":null,\"status\":\"TODO\"}}")))
  })
  public ResponseEntity<ApiResponse<MemberCreateResponseData>> createMember(@RequestBody MemberCreateRequest request) {
    return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).body(new ApiResponse<>(
        "TODO_NOT_IMPLEMENTED",
        "member creation API is reserved for future multi-tenant implementation",
        RequestIdUtil.newRequestId(),
        new MemberCreateResponseData(null, "TODO")));
  }

  @DeleteMapping("/members/{memberId}")
  @Operation(summary = "删除租户成员", description = "删除指定成员（当前为预留接口）")
  @ApiResponses(value = {
      @io.swagger.v3.oas.annotations.responses.ApiResponse(
          responseCode = "501",
          description = "接口预留中",
          content = @Content(mediaType = "application/json", examples = @ExampleObject(value =
              "{\"code\":\"TODO_NOT_IMPLEMENTED\",\"message\":\"member deletion API is reserved for future multi-tenant implementation\",\"requestId\":\"req_20260224_204\",\"data\":{\"memberId\":\"2f4037a8-3973-4f05-a381-f9fef38eb189\",\"deleted\":false}}")))
  })
  public ResponseEntity<ApiResponse<MemberRefResponseData>> deleteMember(
      @Parameter(description = "成员ID（UUID）", example = "2f4037a8-3973-4f05-a381-f9fef38eb189")
      @PathVariable("memberId") String memberId) {
    return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).body(new ApiResponse<>(
        "TODO_NOT_IMPLEMENTED",
        "member deletion API is reserved for future multi-tenant implementation",
        RequestIdUtil.newRequestId(),
        new MemberRefResponseData(memberId, false)));
  }
}


