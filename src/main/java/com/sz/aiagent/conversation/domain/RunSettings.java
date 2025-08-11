package com.sz.aiagent.conversation.domain;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties("travel.runtime")
public class RunSettings {
  private int contextTokens = 16000;
  private int outputTokens = 1600;
  private int safetyTokens = 1000;
  private int runTokens = 50000;
  private int historyTokens = 6500;
  private int retrievalTokens = 2500;
  private int summaryTokens = 1200;
  private int toolResultTokens = 2000;
  private int maxSteps = 8;
  private int maxTools = 8;
  private int timeoutSeconds = 180;
  private int requestsPerHour = 40;
  private String mcpAllowedTools = "";

  @jakarta.annotation.PostConstruct
  public void validate() {
    if (contextTokens < 2048
        || outputTokens < 1
        || safetyTokens < 0
        || contextTokens <= outputTokens + safetyTokens
        || runTokens < outputTokens
        || historyTokens < 128
        || retrievalTokens < 128
        || summaryTokens < 128
        || toolResultTokens < 128
        || maxSteps < 1
        || maxTools < 1
        || timeoutSeconds < 1
        || requestsPerHour < 1) {
      throw new IllegalArgumentException("travel.runtime 中的预算或时限无效");
    }
  }
}
