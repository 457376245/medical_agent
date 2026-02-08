package com.medical.agent.config;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ObservabilityConfig {

  @Bean
  public OpenTelemetry openTelemetry() {
    return OpenTelemetry.noop();
  }

  @Bean
  public Tracer tracer(OpenTelemetry openTelemetry) {
    return openTelemetry.getTracer("medical-agent-backend-java");
  }
}
