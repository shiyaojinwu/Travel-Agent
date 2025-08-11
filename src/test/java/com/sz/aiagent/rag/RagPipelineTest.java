package com.sz.aiagent.rag;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor.CHAT_MEMORY_CONVERSATION_ID_KEY;

import com.sz.aiagent.rag.retrieval.TravelAppContextualQueryAugmenterConfig;
import com.sz.aiagent.rag.retrieval.TravelAppRagCustomAdvisorFactory;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.InMemoryChatMemory;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.chat.model.*;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.*;
import org.springframework.test.util.ReflectionTestUtils;

class RagPipelineTest {
  @Test
  void followUpUsesHistoryToRewriteAndFiltersKnowledgeVersion() {
    ChatModel model = mock(ChatModel.class);
    when(model.call(any(Prompt.class)))
        .thenReturn(
            new ChatResponse(List.of(new Generation(new AssistantMessage("深圳亲子旅游交通建议")))),
            new ChatResponse(List.of(new Generation(new AssistantMessage("建议乘坐地铁。")))));
    VectorStore store = mock(VectorStore.class);
    when(store.similaritySearch(any(SearchRequest.class)))
        .thenReturn(List.of(new Document("doc-1", "深圳景点可乘坐地铁到达", Map.of("filename", "深圳.md"))));
    var factory =
        new TravelAppRagCustomAdvisorFactory(
            new TravelAppContextualQueryAugmenterConfig().travelAppContextualQueryAugmenter(),
            model);
    ReflectionTestUtils.setField(factory, "topK", 3);
    ReflectionTestUtils.setField(factory, "threshold", 0.5);
    ReflectionTestUtils.setField(factory, "version", "v1");
    ReflectionTestUtils.setField(factory, "rewrite", true);
    var memory = new InMemoryChatMemory();
    memory.add("session", List.of(new UserMessage("带孩子去深圳"), new AssistantMessage("可以去深圳湾。")));
    var client =
        ChatClient.builder(model).defaultAdvisors(new MessageChatMemoryAdvisor(memory)).build();
    String answer =
        client
            .prompt()
            .user("那边怎么过去？")
            .advisors(spec -> spec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, "session"))
            .advisors(factory.createTravelRagAdvisor(store))
            .call()
            .content();
    assertThat(answer).isEqualTo("建议乘坐地铁。");
    var queries = org.mockito.ArgumentCaptor.forClass(SearchRequest.class);
    verify(store).similaritySearch(queries.capture());
    assertThat(queries.getValue().getQuery()).isEqualTo("深圳亲子旅游交通建议");
    assertThat(queries.getValue().getFilterExpression().toString())
        .contains("travel-markdown", "v1");
    var prompts = org.mockito.ArgumentCaptor.forClass(Prompt.class);
    verify(model, times(2)).call(prompts.capture());
    assertThat(prompts.getAllValues().getFirst().getContents()).contains("深圳", "那边怎么过去");
  }
}
