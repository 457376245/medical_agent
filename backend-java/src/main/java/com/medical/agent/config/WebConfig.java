package com.medical.agent.config;

import com.medical.agent.infrastructure.idempotency.IdempotencyInterceptor;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
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

  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration apiCors = new CorsConfiguration();
    apiCors.setAllowedOrigins(allowedOrigins);
    apiCors.setAllowedMethods(List.of("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS"));
    apiCors.setAllowedHeaders(List.of("*"));
    apiCors.setExposedHeaders(List.of("requestId"));
    apiCors.setAllowCredentials(true);
    apiCors.setMaxAge(3600L);

    CorsConfiguration uploadCors = new CorsConfiguration();
    uploadCors.setAllowedOrigins(allowedOrigins);
    uploadCors.setAllowedMethods(List.of("PUT", "OPTIONS"));
    uploadCors.setAllowedHeaders(List.of("*"));
    uploadCors.setAllowCredentials(true);
    uploadCors.setMaxAge(3600L);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/api/**", apiCors);
    source.registerCorsConfiguration("/mock-upload/**", uploadCors);
    return source;
  }
}
