package com.sz.aiagent.agent;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.sz.aiagent.agent.model.AgentState;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.ToolCallbacks;
import org.springframework.ai.tool.annotation.Tool;

class AgentLoopTest {
  public static class Lookup {
    int calls;

    @Tool(description = "Local test lookup")
    public String lookup() {
      calls++;
      return "深圳";
    }

    @Tool(description = "Stop")
    public String doTerminate() {
      calls++;
      return "已完成";
    }
  }

  private ChatResponse text(String value) {
    return new ChatResponse(List.of(new Generation(new AssistantMessage(value))));
  }

  private ChatResponse tool(String name) {
    return new ChatResponse(
        List.of(
            new Generation(
                new AssistantMessage(
                    "",
                    Map.of(),
                    List.of(new AssistantMessage.ToolCall("call-1", "function", name, "{}"))))));
  }

  private ToolCallAgent agent(ChatModel model, Lookup tools) {
    var agent = new ToolCallAgent(ToolCallbacks.from(tools), null);
    agent.setChatClient(ChatClient.builder(model).build());
    agent.setSystemPrompt("Test assistant");
    return agent;
  }

  @Test
  void directAnswerFinishesAfterOneModelCall() {
    var model = mock(ChatModel.class);
    when(model.call(any(Prompt.class))).thenReturn(text("欢迎来深圳"));
    var tools = new Lookup();
    var agent = agent(model, tools);
    assertThat(agent.run("你好")).isEqualTo("欢迎来深圳");
    assertThat(agent.getState()).isEqualTo(AgentState.FINISHED);
    assertThat(tools.calls).isZero();
    verify(model, times(1)).call(any(Prompt.class));
  }

  @Test
  void toolResultIsReturnedToModelBeforeFinalAnswer() {
    var model = mock(ChatModel.class);
    when(model.call(any(Prompt.class))).thenReturn(tool("lookup"), text("深圳行程"));
    var tools = new Lookup();
    var agent = agent(model, tools);
    assertThat(agent.run("帮我查询")).endsWith("深圳行程");
    assertThat(tools.calls).isEqualTo(1);
    assertThat(agent.getState()).isEqualTo(AgentState.FINISHED);
    var prompts = org.mockito.ArgumentCaptor.forClass(Prompt.class);
    verify(model, times(2)).call(prompts.capture());
    assertThat(prompts.getAllValues().get(1).getInstructions())
        .anyMatch(m -> m instanceof ToolResponseMessage);
  }

  @Test
  void failedModelIsErrorAndNotARepeatedSuccess() {
    var model = mock(ChatModel.class);
    when(model.call(any(Prompt.class))).thenThrow(new IllegalStateException("unavailable"));
    var agent = agent(model, new Lookup());
    assertThatThrownBy(() -> agent.run("test")).isInstanceOf(IllegalStateException.class);
    assertThat(agent.getState()).isEqualTo(AgentState.ERROR);
    verify(model, times(1)).call(any(Prompt.class));
  }

  @Test
  void finishingOnLastAllowedStepDoesNotReportStepLimit() {
    var model = mock(ChatModel.class);
    when(model.call(any(Prompt.class))).thenReturn(text("完成"));
    var agent = agent(model, new Lookup());
    agent.setMaxSteps(1);
    assertThat(agent.run("test")).isEqualTo("完成");
    assertThat(agent.getState()).isEqualTo(AgentState.FINISHED);
  }

  @Test
  void stepLimitIsNotSuccessfulCompletion() {
    var model = mock(ChatModel.class);
    when(model.call(any(Prompt.class))).thenReturn(tool("lookup"));
    var agent = agent(model, new Lookup());
    agent.setMaxSteps(1);
    assertThat(agent.run("test")).contains("任务可能尚未完成");
    assertThat(agent.getState()).isEqualTo(AgentState.LIMIT_REACHED);
    verify(model, times(1)).call(any(Prompt.class));
  }

  @Test
  void explicitTerminateStopsWithoutAnotherModelCall() {
    var model = mock(ChatModel.class);
    when(model.call(any(Prompt.class))).thenReturn(tool("doTerminate"));
    var agent = agent(model, new Lookup());
    agent.run("test");
    assertThat(agent.getState()).isEqualTo(AgentState.FINISHED);
    verify(model, times(1)).call(any(Prompt.class));
  }

  @Test
  void cancellationDuringModelCallDoesNotExecuteReturnedTools() {
    var model = mock(ChatModel.class);
    var tools = new Lookup();
    var agent = agent(model, tools);
    when(model.call(any(Prompt.class)))
        .thenAnswer(
            call -> {
              agent.cancel();
              return tool("lookup");
            });
    agent.run("test");
    assertThat(tools.calls).isZero();
    assertThat(agent.getState()).isEqualTo(AgentState.CANCELLED);
  }

  @Test
  void unknownToolFailsClosed() {
    var model = mock(ChatModel.class);
    when(model.call(any(Prompt.class))).thenReturn(tool("runTerminalCommand"));
    var tools = new Lookup();
    var agent = agent(model, tools);
    assertThatThrownBy(() -> agent.run("test")).isInstanceOf(IllegalStateException.class);
    assertThat(tools.calls).isZero();
    assertThat(agent.getState()).isEqualTo(AgentState.ERROR);
  }

  @Test
  void invalidInputNeverSchedulesModelWork() {
    var model = mock(ChatModel.class);
    var agent = agent(model, new Lookup());
    assertThatThrownBy(() -> agent.run(" ")).isInstanceOf(IllegalArgumentException.class);
    assertThat(agent.getState()).isEqualTo(AgentState.IDLE);
    verify(model, never()).call(any(Prompt.class));
  }
}
