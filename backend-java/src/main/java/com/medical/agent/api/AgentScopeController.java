package com.medical.agent.api;

import com.medical.agent.application.AgentScopeService;
import com.medical.agent.domain.dto.response.AgentScopeResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import java.util.List;

@RestController
@RequestMapping("/internal/agent/scope")
public class AgentScopeController {
  private final AgentScopeService agentScopeService;
  private final InternalAgentApiGuard internalAgentApiGuard;

  public AgentScopeController(AgentScopeService agentScopeService, InternalAgentApiGuard internalAgentApiGuard) {
    this.agentScopeService = agentScopeService;
    this.internalAgentApiGuard = internalAgentApiGuard;
  }

  @GetMapping("/verify")
  public AgentScopeResponse verify(HttpServletRequest request) {
    return agentScopeService.verify(
        request.getHeader("Authorization"),
        request.getHeader("X-Patient-Id"));
  }

  @PostMapping("/attachments")
  public AttachmentAuthorizationResponse authorizeAttachments(
      @RequestBody AttachmentAuthorizationRequest body,
      HttpServletRequest request) {
    internalAgentApiGuard.verifyAndApplyScope(request);
    return new AttachmentAuthorizationResponse(
        agentScopeService.authorizeAttachmentKeys(body == null ? List.of() : body.objectKeys()));
  }

  public record AttachmentAuthorizationRequest(List<String> objectKeys) {}
  public record AttachmentAuthorizationResponse(List<String> authorizedObjectKeys) {}
}
