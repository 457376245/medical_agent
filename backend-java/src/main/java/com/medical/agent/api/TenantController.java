package com.medical.agent.api;

import com.medical.agent.domain.dto.ApiResponse;
import com.medical.agent.domain.dto.request.MemberCreateRequest;
import com.medical.agent.domain.dto.response.MemberCreateResponseData;
import com.medical.agent.domain.dto.response.MemberListResponseData;
import com.medical.agent.domain.dto.response.MemberRefResponseData;
import com.medical.agent.domain.dto.response.TenantInfoResponseData;
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
public class TenantController {
  @GetMapping
  public ResponseEntity<ApiResponse<TenantInfoResponseData>> currentTenant() {
    return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).body(new ApiResponse<>(
        "TODO_NOT_IMPLEMENTED",
        "tenant profile API is reserved for future multi-tenant implementation",
        RequestIdUtil.newRequestId(),
        new TenantInfoResponseData(null, null, "TODO")));
  }

  @GetMapping("/members")
  public ResponseEntity<ApiResponse<MemberListResponseData>> listMembers() {
    return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).body(new ApiResponse<>(
        "TODO_NOT_IMPLEMENTED",
        "member listing API is reserved for future multi-tenant implementation",
        RequestIdUtil.newRequestId(),
        new MemberListResponseData(List.of())));
  }

  @PostMapping("/members")
  public ResponseEntity<ApiResponse<MemberCreateResponseData>> createMember(@RequestBody MemberCreateRequest request) {
    return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).body(new ApiResponse<>(
        "TODO_NOT_IMPLEMENTED",
        "member creation API is reserved for future multi-tenant implementation",
        RequestIdUtil.newRequestId(),
        new MemberCreateResponseData(null, "TODO")));
  }

  @DeleteMapping("/members/{memberId}")
  public ResponseEntity<ApiResponse<MemberRefResponseData>> deleteMember(@PathVariable("memberId") String memberId) {
    return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).body(new ApiResponse<>(
        "TODO_NOT_IMPLEMENTED",
        "member deletion API is reserved for future multi-tenant implementation",
        RequestIdUtil.newRequestId(),
        new MemberRefResponseData(memberId, false)));
  }
}


