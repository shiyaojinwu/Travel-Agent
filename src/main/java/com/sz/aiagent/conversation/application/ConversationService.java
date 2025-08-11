package com.sz.aiagent.conversation.application;

import com.sz.aiagent.conversation.domain.*;
import com.sz.aiagent.conversation.infrastructure.ConversationStore;
import jakarta.annotation.PreDestroy;
import java.util.*;
import java.util.concurrent.*;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

@Service
public class ConversationService {
  private final ConversationStore store;
  private final ConversationEngine engine;
  private final ExecutorService executor;
  private final RunSettings settings;
  private final PromptCatalog prompts;
  private final Map<String, FutureTask<Void>> tasks = new ConcurrentHashMap<>();
  private final ScheduledExecutorService watchdog =
      Executors.newSingleThreadScheduledExecutor(
          Thread.ofPlatform().name("run-deadline").factory());

  @Value("${spring.ai.dashscope.chat.options.model:qwen-plus}")
  private String model;

  @Value("${travel.rag.version:v1}")
  private String knowledge;

  public ConversationService(
      ConversationStore store,
      ConversationEngine engine,
      ExecutorService agentExecutor,
      RunSettings settings,
      PromptCatalog prompts) {
    this.store = store;
    this.engine = engine;
    this.executor = agentExecutor;
    this.settings = settings;
    this.prompts = prompts;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void recover() {
    store.recover();
  }

  public ConversationStore.Run send(String owner, String cid, String key, String text) {
    var result =
        store.start(
            owner,
            cid,
            key,
            text,
            model,
            prompts.version,
            knowledge,
            engine.toolPolicy(),
            settings.getRequestsPerHour());
    if (!result.created()) return result.run();
    var run = result.run();
    String mode = store.conversation(owner, cid).mode();
    var task =
        new FutureTask<Void>(
            () -> {
              if (!store.claim(run.id())) return null;
              try {
                engine.execute(store.run(run.id()), mode);
              } catch (CancellationException e) {
                store.finish(run.id(), "CANCELLED", "CANCELLED", null);
              } catch (ApiFailure e) {
                store.event(run.id(), "notice", Map.of("content", e.getMessage()));
                store.finish(run.id(), "FAILED", e.code, null);
              } catch (Exception e) {
                LoggerFactory.getLogger(getClass())
                    .warn("Run {} failed: {}", run.id(), e.getClass().getSimpleName());
                store.finish(run.id(), "FAILED", "PROVIDER_ERROR", null);
              } finally {
                tasks.remove(run.id());
              }
              return null;
            });
    tasks.put(run.id(), task);
    var deadline =
        watchdog.schedule(
            () -> {
              store.finish(run.id(), "FAILED", "DEADLINE_EXCEEDED", null);
              task.cancel(true);
              tasks.remove(run.id());
            },
            settings.getTimeoutSeconds(),
            TimeUnit.SECONDS);
    // Release the scheduled timer once the task has actually finished, including queued
    // cancellation.
    try {
      executor.execute(
          () -> {
            try {
              task.run();
            } finally {
              deadline.cancel(false);
              tasks.remove(run.id());
            }
          });
    } catch (RejectedExecutionException e) {
      deadline.cancel(false);
      tasks.remove(run.id());
      store.finish(run.id(), "FAILED", "SERVER_BUSY", null);
    }
    return store.run(run.id());
  }

  public void cancel(String owner, String id) {
    store.ownedRun(owner, id);
    store.finish(id, "CANCELLED", "CANCELLED", null);
    var task = tasks.remove(id);
    if (task != null) task.cancel(true);
  }

  @PreDestroy
  public void close() {
    watchdog.shutdownNow();
    tasks.forEach(
        (id, task) -> {
          store.finish(id, "INTERRUPTED", "SERVER_SHUTDOWN", null);
          task.cancel(true);
        });
  }
}
