package com.sz.aiagent.agent;

import com.sz.aiagent.agent.model.AgentState;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.FutureTask;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** One instance represents one run. Terminal states never start another model/tool step. */
@Data
@Slf4j
public abstract class BaseAgent {
  private String name;
  private String systemPrompt;
  private String nextStepPrompt;
  private volatile AgentState state = AgentState.IDLE;
  private int currentStep;
  private int maxSteps = 10;
  private ChatClient chatClient;
  private List<Message> messageList = new ArrayList<>();

  private synchronized void start(String prompt) {
    if (state != AgentState.IDLE) throw new IllegalStateException("当前状态不可运行: " + state);
    if (prompt == null || prompt.isBlank()) throw new IllegalArgumentException("用户输入不能为空");
    if (maxSteps < 1) throw new IllegalArgumentException("最大步骤数必须大于零");
    state = AgentState.RUNNING;
    messageList.add(new UserMessage(prompt));
  }

  public synchronized void cancel() {
    if (state == AgentState.RUNNING) state = AgentState.CANCELLED;
  }

  @FunctionalInterface
  private interface Output {
    void send(String event, String content) throws Exception;
  }

  private void execute(Output output) throws Exception {
    while (state == AgentState.RUNNING && currentStep < maxSteps) {
      if (Thread.currentThread().isInterrupted()) {
        cancel();
        break;
      }
      currentStep++;
      String content = step();
      if (state == AgentState.CANCELLED) break;
      if (state == AgentState.ERROR) throw new IllegalStateException("步骤执行失败");
      output.send(state == AgentState.FINISHED ? "final" : "step", content);
    }
    if (state == AgentState.RUNNING) {
      state = AgentState.LIMIT_REACHED;
      output.send("limit", "已停止：达到最大执行步数（" + maxSteps + "），任务可能尚未完成。");
    }
  }

  public String run(String userPrompt) {
    start(userPrompt);
    List<String> output = new ArrayList<>();
    try {
      execute((event, text) -> output.add(text));
      return String.join("\n", output);
    } catch (Exception e) {
      if (state != AgentState.CANCELLED) state = AgentState.ERROR;
      throw new IllegalStateException("Agent 执行失败", e);
    } finally {
      cleanup();
    }
  }

  public SseEmitter runStream(
      String userPrompt,
      ExecutorService executor,
      java.util.function.Consumer<Runnable> registerCancel) {
    start(userPrompt);
    SseEmitter emitter = new SseEmitter(180000L);
    FutureTask<Void> task =
        new FutureTask<>(
            () -> {
              try {
                execute(
                    (event, text) ->
                        emitter.send(
                            SseEmitter.event()
                                .name(event)
                                .data(Map.of("content", text, "step", currentStep))));
                if (state != AgentState.CANCELLED) {
                  emitter.send(SseEmitter.event().name("done").data(Map.of("state", state.name())));
                  emitter.complete();
                }
              } catch (Exception e) {
                if (state != AgentState.CANCELLED) {
                  state = AgentState.ERROR;
                  log.warn("Agent run failed: {}", e.getClass().getSimpleName());
                  try {
                    emitter.send(
                        SseEmitter.event()
                            .name("failure")
                            .data(Map.of("content", "生成失败，请检查服务配置后重试。")));
                    emitter.complete();
                  } catch (Exception sendError) {
                    emitter.completeWithError(sendError);
                  }
                }
              } finally {
                cleanup();
              }
              return null;
            });
    Runnable stop =
        () -> {
          cancel();
          task.cancel(true);
        };
    emitter.onTimeout(
        () -> {
          stop.run();
          emitter.complete();
        });
    emitter.onError(error -> stop.run());
    emitter.onCompletion(stop);
    registerCancel.accept(
        () -> {
          stop.run();
          emitter.complete();
        });
    try {
      executor.execute(task);
    } catch (java.util.concurrent.RejectedExecutionException e) {
      state = AgentState.ERROR;
      task.cancel(true);
      cleanup();
      throw new org.springframework.web.server.ResponseStatusException(
          org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "服务繁忙，请稍后重试");
    }
    return emitter;
  }

  public abstract String step();

  protected void cleanup() {}
}
