package com.medical.agent.config;

import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
public class StorageEncryptionConfig {
  private final Environment environment;

  public StorageEncryptionConfig(Environment environment) {
    this.environment = environment;
  }

  @PostConstruct
  public void validateEncryptionFlags() {
    environment.getProperty("storage.encryption.enabled", "true");
    environment.getProperty("database.encryption.enabled", "true");
  }
}
