package com.medical.agent.config;

import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TlsConfig {

  @Bean
  public TomcatServletWebServerFactory servletContainer() {
    return new TomcatServletWebServerFactory();
  }
}
