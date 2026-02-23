package com.medical.agent.domain.dto.response;

import java.util.List;

public record MemberListResponseData(List<MemberSummary> members) {
  public record MemberSummary(String memberId, String email, String displayName, String role, String status) {}
}
