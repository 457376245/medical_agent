package com.medical.agent.domain.util;

public final class TextUtils {
  private TextUtils() {}

  public static String trimToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
