package com.sz.aiagent.config;

import java.util.concurrent.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AgentExecutionConfig {
  @Bean(destroyMethod = "shutdownNow")
  public ExecutorService agentExecutor() {
    return new ThreadPoolExecutor(
        4,
        4,
        30,
        TimeUnit.SECONDS,
        new ArrayBlockingQueue<>(16),
        Thread.ofPlatform().name("travel-agent-", 0).factory(),
        new ThreadPoolExecutor.AbortPolicy());
  }
}
