package com.sz.aiagent.conversation.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
public class PromptCatalog {
  public final String planner = read("planner-v1"),
      summary = read("summary-v1"),
      rewrite = read("rewrite-v1");
  public final String version = hash(planner + summary + rewrite);

  private static String read(String name) {
    try {
      return new ClassPathResource("prompts/" + name + ".txt")
          .getContentAsString(StandardCharsets.UTF_8);
    } catch (Exception e) {
      throw new IllegalStateException("无法读取提示词", e);
    }
  }

  private static String hash(String text) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
