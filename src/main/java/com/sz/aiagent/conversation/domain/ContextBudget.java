package com.sz.aiagent.conversation.domain;

import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.ai.chat.messages.*;

public final class ContextBudget {
  private ContextBudget() {}

  // Conservative UTF-8 byte estimate. This is deliberately not reported as provider usage.
  public static int tokens(String text) {
    return text == null ? 0 : text.getBytes(StandardCharsets.UTF_8).length;
  }

  public static int tokens(List<Message> messages) {
    return messages.stream()
        .mapToInt(
            m ->
                tokens(m.getText())
                    + 16
                    + (m instanceof AssistantMessage a ? tokens(a.getToolCalls().toString()) : 0)
                    + (m instanceof ToolResponseMessage t
                        ? tokens(t.getResponses().toString())
                        : 0))
        .sum();
  }

  public static String clip(String text, int budget) {
    if (text == null) return "";
    if (tokens(text) <= budget) return text;
    String suffix = "\n[内容已截断]";
    int available = Math.max(0, budget - tokens(suffix)), end = 0, used = 0;
    while (end < text.length()) {
      int cp = text.codePointAt(end);
      int count = tokens(new String(Character.toChars(cp)));
      if (used + count > available) break;
      used += count;
      end += Character.charCount(cp);
    }
    return text.substring(0, end) + suffix;
  }

  public static void check(
      List<Message> messages, int toolTokens, RunSettings settings, int output) {
    if (tokens(messages) + toolTokens + output + settings.getSafetyTokens()
        > settings.getContextTokens())
      throw new ApiFailure(422, "CONTEXT_BUDGET", "本轮内容超出上下文预算，请缩短消息或新建旅行会话。");
  }
}
