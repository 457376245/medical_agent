package com.medical.agent.config;

import com.medical.agent.infrastructure.idempotency.IdempotencyInterceptor;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
  private final IdempotencyInterceptor idempotencyInterceptor;
  private final List<String> allowedOrigins;

  public WebConfig(
      IdempotencyInterceptor idempotencyInterceptor,
      @Value("${app.cors.allowed-origins:http://localhost:3000,http://127.0.0.1:3000}") String origins) {
    this.idempotencyInterceptor = idempotencyInterceptor;
    this.allowedOrigins = Arrays.stream(origins.split(","))
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .toList();
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(idempotencyInterceptor);
  }

  @Override
  public void addCorsMappings(CorsRegistry registry) {
    registry.addMapping("/api/**")
        .allowedOrigins(allowedOrigins.toArray(new String[0]))
        .allowedMethods("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS")
        .allowedHeaders("*")
        .exposedHeaders("requestId")
        .allowCredentials(false)
        .maxAge(3600);

    registry.addMapping("/mock-upload/**")
        .allowedOrigins(allowedOrigins.toArray(new String[0]))
        .allowedMethods("PUT", "OPTIONS")
        .allowedHeaders("*")
        .allowCredentials(false)
        .maxAge(3600);
  }
}
