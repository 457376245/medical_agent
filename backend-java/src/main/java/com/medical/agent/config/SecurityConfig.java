package com.medical.agent.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {
  private final Environment environment;

  public SecurityConfig(Environment environment) {
    this.environment = environment;
  }

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    boolean securityEnabled = Boolean.parseBoolean(environment.getProperty("app.security.enabled", "true"));

    http.csrf(csrf -> csrf.disable())
        .cors(Customizer.withDefaults())
        .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

    if (securityEnabled) {
      http.authorizeHttpRequests(auth -> auth
          .requestMatchers("/actuator/health").permitAll()
          .anyRequest().authenticated());
    } else {
      http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
    }
    return http.build();
  }
}
