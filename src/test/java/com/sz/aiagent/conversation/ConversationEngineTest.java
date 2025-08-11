package com.sz.aiagent.conversation;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sz.aiagent.conversation.application.*;
import com.sz.aiagent.conversation.domain.*;
import com.sz.aiagent.conversation.infrastructure.ConversationStore;
import java.util.*;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.chat.model.*;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.*;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;

class ConversationEngineTest {
  ChatModel model;
  ConversationStore store;
  VectorStore vectors;
  RunSettings cfg;
  ConversationEngine engine;
  ConversationStore.Run run;

  ChatResponse text(String text) {
    return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
  }

  public static class Lookup {
    int count;

    @Tool(description = "search")
    public String searchWeb(String query) {
      count++;
      return "深圳信息";
    }
  }

  Lookup lookup;

  @BeforeEach
  void setup() {
    model = mock(ChatModel.class);
    store = mock(ConversationStore.class);
    vectors = mock(VectorStore.class);
    cfg = new RunSettings();
    lookup = new Lookup();
    ObjectProvider<ToolCallbackProvider> mcp = mock(ObjectProvider.class);
    engine =
        new ConversationEngine(
            model,
            store,
            cfg,
            new PromptCatalog(),
            vectors,
            ToolCallbacks.from(lookup),
            mcp,
            new ObjectMapper());
    ReflectionTestUtils.setField(engine, "topK", 3);
    ReflectionTestUtils.setField(engine, "threshold", 0.5);
    when(store.isActive("run")).thenReturn(true);
    when(store.summary("cid")).thenReturn(new ConversationStore.Summary(0, 0, ""));
    when(store.history("cid")).thenReturn(List.of());
    run =
        new ConversationStore.Run(
            "run",
            "cid",
            "第二天下雨，预算降到1500",
            "深圳，周末，两个人",
            "RUNNING",
            "test",
            "p",
            "v1",
            "readonly",
            null,
            0);
  }

  @Test
  void followUpIncludesStoredHistoryAndExactTripConstraints() {
    when(store.history("cid"))
        .thenReturn(
            List.of(
                new ConversationStore.Message(1, "user", "去深圳", "SAVED", "old"),
                new ConversationStore.Message(2, "assistant", "第二天去海边", "FINISHED", "old")));
    when(model.call(any(Prompt.class))).thenReturn(text("第二天改为室内"));
    engine.execute(run, "AGENT");
    var prompt = ArgumentCaptor.forClass(Prompt.class);
    verify(model).call(prompt.capture());
    assertThat(prompt.getValue().getInstructions().stream().map(Message::getText).toList())
        .anyMatch(t -> t.contains("深圳，周末，两个人"))
        .contains("去深圳", "第二天去海边", run.text());
    verify(store).finish("run", "FINISHED", null, "第二天改为室内");
    verify(store)
        .call(
            eq("run"),
            eq("agent"),
            anyInt(),
            anyInt(),
            eq(true),
            eq("FINISHED"),
            anyLong(),
            isNull());
  }

  @Test
  void tokenBudgetRejectsBeforeAnyPaidModelCall() {
    cfg.setRunTokens(1);
    assertThatThrownBy(() -> engine.execute(run, "AGENT"))
        .isInstanceOf(ApiFailure.class)
        .hasMessageContaining("Token");
    verify(model, never()).call(any(Prompt.class));
    verify(model, never()).stream(any(Prompt.class));
  }

  @Test
  void unknownToolIsDeniedAndRecorded() {
    when(model.call(any(Prompt.class)))
        .thenReturn(
            new ChatResponse(
                List.of(
                    new Generation(
                        new AssistantMessage(
                            "",
                            Map.of(),
                            List.of(
                                new AssistantMessage.ToolCall("x", "function", "shell", "{}")))))));
    assertThatThrownBy(() -> engine.execute(run, "AGENT"))
        .isInstanceOf(ApiFailure.class)
        .hasMessageContaining("未授权");
    assertThat(lookup.count).isZero();
    verify(store).tool("run", "shell", "DENIED", 0);
  }

  @Test
  void cancelDuringModelResponsePreventsToolExecution() {
    when(model.call(any(Prompt.class)))
        .thenAnswer(
            i -> {
              when(store.isActive("run")).thenReturn(false);
              return new ChatResponse(
                  List.of(
                      new Generation(
                          new AssistantMessage(
                              "",
                              Map.of(),
                              List.of(
                                  new AssistantMessage.ToolCall(
                                      "x", "function", "searchWeb", "{\"query\":\"深圳\"}"))))));
            });
    assertThatThrownBy(() -> engine.execute(run, "AGENT"))
        .isInstanceOf(java.util.concurrent.CancellationException.class);
    assertThat(lookup.count).isZero();
  }

  @Test
  void ragUsesVersionFilterAndStreamsAnswer() {
    when(vectors.similaritySearch(any(org.springframework.ai.vectorstore.SearchRequest.class)))
        .thenReturn(List.of());
    when(model.stream(any(Prompt.class))).thenReturn(Flux.just(text("深圳"), text("行程")));
    engine.execute(run, "CHAT");
    var query = ArgumentCaptor.forClass(org.springframework.ai.vectorstore.SearchRequest.class);
    verify(vectors).similaritySearch(query.capture());
    assertThat(query.getValue().getFilterExpression().toString())
        .contains("version", "v1", "dataset");
    verify(store).event("run", "delta", Map.of("content", "深圳行程"));
    verify(store).finish("run", "FINISHED", null, null);
  }

  @Test
  void summaryCoverageExcludesArchivedRoundsFromInjectedHistory() {
    when(store.summary("cid")).thenReturn(new ConversationStore.Summary(2, 2, "已决定深圳两人游"));
    when(store.history("cid"))
        .thenReturn(
            List.of(
                new ConversationStore.Message(1, "user", "旧消息不重复", "SAVED", "old"),
                new ConversationStore.Message(2, "assistant", "旧回答不重复", "FINISHED", "old"),
                new ConversationStore.Message(3, "user", "近期消息完整保留", "SAVED", "recent"),
                new ConversationStore.Message(4, "assistant", "近期回答完整保留", "FINISHED", "recent")));
    when(model.call(any(Prompt.class))).thenReturn(text("继续"));
    engine.execute(run, "AGENT");
    var input = ArgumentCaptor.forClass(Prompt.class);
    verify(model).call(input.capture());
    var all = input.getValue().getInstructions().stream().map(Message::getText).toList();
    assertThat(all)
        .contains("近期消息完整保留", "近期回答完整保留")
        .doesNotContain("旧消息不重复", "旧回答不重复")
        .anyMatch(s -> s.contains("已决定深圳两人游"));
  }

  @Test
  void failedSummaryDoesNotAdvanceCoverage() {
    cfg.setHistoryTokens(1);
    when(store.history("cid"))
        .thenReturn(
            List.of(
                new ConversationStore.Message(1, "user", "预算1500", "SAVED", "old"),
                new ConversationStore.Message(2, "assistant", "建议", "FINISHED", "old")));
    when(model.call(any(Prompt.class))).thenThrow(new IllegalStateException("offline"));
    assertThatThrownBy(() -> engine.execute(run, "AGENT"))
        .isInstanceOf(IllegalStateException.class);
    verify(store, never()).summary(anyString(), anyLong(), anyString());
  }

  @Test
  void clippingIsBoundedAndPreservesUnicode() {
    String clipped = ContextBudget.clip("深圳😀".repeat(100), 100);
    assertThat(ContextBudget.tokens(clipped)).isLessThanOrEqualTo(100);
    assertThat(clipped).endsWith("[内容已截断]").doesNotContain("�");
  }
}
