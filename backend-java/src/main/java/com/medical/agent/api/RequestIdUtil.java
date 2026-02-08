package com.medical.agent.api;

import java.util.UUID;

public final class RequestIdUtil {
  private RequestIdUtil() {}

  public static String newRequestId() {
    return UUID.randomUUID().toString();
  }
}
