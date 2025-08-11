package com.sz.aiagent.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig implements WebMvcConfigurer {
  @Value("${travel.cors-origins:http://localhost:5173,http://127.0.0.1:5173}")
  private String[] origins;

  @org.springframework.context.annotation.Bean
  public org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor sseWriterExecutor() {
    var executor = new org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor();
    executor.setCorePoolSize(2);
    executor.setMaxPoolSize(8);
    executor.setQueueCapacity(128);
    executor.setThreadNamePrefix("sse-writer-");
    executor.initialize();
    return executor;
  }

  @Override
  public void configureAsyncSupport(
      org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer configurer) {
    configurer.setTaskExecutor(sseWriterExecutor()).setDefaultTimeout(300000);
  }

  @Override
  public void addCorsMappings(CorsRegistry registry) {
    registry
        .addMapping("/**")
        .allowedOrigins(origins)
        .allowCredentials(true)
        .allowedMethods("GET", "POST", "DELETE", "PATCH", "OPTIONS")
        .allowedHeaders("Content-Type", "X-Travel-Client", "Last-Event-ID")
        .maxAge(3600);
  }
}
