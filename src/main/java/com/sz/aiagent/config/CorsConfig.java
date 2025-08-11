package com.sz.aiagent.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig implements WebMvcConfigurer {
  @Value("${travel.cors-origins:http://localhost:5173,http://127.0.0.1:5173}")
  private String[] origins;

  @Override
  public void addCorsMappings(CorsRegistry registry) {
    registry
        .addMapping("/**")
        .allowedOrigins(origins)
        .allowCredentials(true)
        .allowedMethods("GET", "POST", "DELETE", "OPTIONS")
        .allowedHeaders("Content-Type")
        .maxAge(3600);
  }
}
