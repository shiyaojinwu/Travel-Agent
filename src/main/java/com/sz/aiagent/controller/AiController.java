package com.sz.aiagent.controller;

import com.sz.aiagent.agent.Manus;
import com.sz.aiagent.app.TravelApp;
import jakarta.servlet.http.HttpSession;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposables;
import reactor.core.publisher.Flux;

@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
    name = "travel.legacy-api-enabled",
    havingValue = "true")
@RestController
@RequestMapping("/ai")
public class AiController {
  private final TravelApp travelApp;
  private final ToolCallback[] tools;
  private final ChatModel model;
  private final ToolCallbackProvider mcp;
  private final ExecutorService executor;
  private final com.sz.aiagent.service.RunRegistry runs;

  public AiController(
      TravelApp travelApp,
      ToolCallback[] allTools,
      ChatModel dashscopeChatModel,
      ObjectProvider<ToolCallbackProvider> mcp,
      ExecutorService agentExecutor,
      com.sz.aiagent.service.RunRegistry runs) {
    this.travelApp = travelApp;
    this.tools = allTools;
    this.model = dashscopeChatModel;
    this.mcp = mcp.getIfAvailable();
    this.executor = agentExecutor;
    this.runs = runs;
  }

  private void validate(String message) {
    if (message == null || message.isBlank() || message.length() > 8000)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "消息长度应为 1–8000 字符");
  }

  private String conversationKey(String chatId, HttpSession session) {
    if (chatId == null || !chatId.matches("[A-Za-z0-9-]{1,80}"))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "无效会话 ID");
    return session.getId() + ":" + chatId;
  }

  @GetMapping("/chat/sync")
  public String chat(
      @RequestParam String message, @RequestParam String chatId, HttpSession session) {
    validate(message);
    return travelApp.doChat(message, conversationKey(chatId, session));
  }

  @GetMapping(
      value = {"/chat/sse", "/chat/server_sent_event"},
      produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public Flux<ServerSentEvent<Map<String, String>>> stream(
      @RequestParam String message, @RequestParam String chatId, HttpSession session) {
    validate(message);
    return travelApp
        .doChatByStream(message, conversationKey(chatId, session))
        .timeout(Duration.ofSeconds(120))
        .map(chunk -> ServerSentEvent.builder(Map.of("content", chunk)).event("delta").build())
        .concatWithValues(
            ServerSentEvent.builder(Map.of("state", "FINISHED")).event("done").build())
        .onErrorResume(
            error ->
                Flux.just(
                    ServerSentEvent.builder(Map.of("content", "生成失败，请稍后重试。"))
                        .event("failure")
                        .build()));
  }

  @GetMapping(value = "/chat/sse_emitter", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter emitter(
      @RequestParam String message,
      @RequestParam String chatId,
      @RequestParam String runId,
      HttpSession session) {
    Flux<ServerSentEvent<Map<String, String>>> source = stream(message, chatId, session);
    SseEmitter emitter = new SseEmitter(180000L);
    var subscription = Disposables.swap();
    var registration = runs.register(session.getId(), runId);
    registration.onCancel(
        () -> {
          subscription.dispose();
          emitter.complete();
        });
    emitter.onCompletion(registration::complete);
    emitter.onTimeout(registration::complete);
    emitter.onError(error -> registration.complete());
    emitter.onCompletion(subscription::dispose);
    emitter.onTimeout(
        () -> {
          subscription.dispose();
          emitter.complete();
        });
    emitter.onError(error -> subscription.dispose());
    subscription.update(
        source.subscribe(
            event -> {
              try {
                emitter.send(SseEmitter.event().name(event.event()).data(event.data()));
              } catch (Exception e) {
                subscription.dispose();
                emitter.completeWithError(e);
              }
            },
            emitter::completeWithError,
            emitter::complete));
    return emitter;
  }

  @GetMapping(value = "/manus/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter agent(
      @RequestParam String message, @RequestParam String runId, HttpSession session) {
    validate(message);
    var registration = runs.register(session.getId(), runId);
    try {
      SseEmitter emitter =
          new Manus(tools, model, mcp).runStream(message, executor, registration::onCancel);
      emitter.onCompletion(registration::complete);
      emitter.onTimeout(registration::complete);
      emitter.onError(error -> registration.complete());
      return emitter;
    } catch (RuntimeException e) {
      registration.complete();
      throw e;
    }
  }

  @DeleteMapping("/runs/{runId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void cancel(@PathVariable String runId, HttpSession session) {
    runs.cancel(session.getId(), runId);
  }
}
