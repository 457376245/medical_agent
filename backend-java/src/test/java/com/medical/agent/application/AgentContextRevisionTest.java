package com.medical.agent.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

class AgentContextRevisionTest {
  @Test
  void revisionHashIsStableAndChangesWithContextData() {
    String first = AgentDiseaseProfileContextService.sha256("profile|medication-a|updated-1");

    assertEquals(first, AgentDiseaseProfileContextService.sha256("profile|medication-a|updated-1"));
    assertNotEquals(first, AgentDiseaseProfileContextService.sha256("profile|medication-b|updated-2"));
  }
}
