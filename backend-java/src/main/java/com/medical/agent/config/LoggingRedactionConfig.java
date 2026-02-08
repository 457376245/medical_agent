package com.medical.agent.config;

import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LoggingRedactionConfig {

  @Bean(name = "redactionKeywords")
  public List<String> redactionKeywords() {
    return List.of("payload_json", "medical_text", "raw_content", "diagnosis_text");
  }
}
