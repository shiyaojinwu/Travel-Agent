package com.sz.aiagent.conversation;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sz.aiagent.conversation.infrastructure.ConversationStore;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.*;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.vectorstore.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import reactor.core.publisher.Flux;

@SpringBootTest(
    properties = {
      "spring.ai.dashscope.api-key=offline",
      "spring.ai.mcp.client.enabled=false",
      "spring.datasource.url=jdbc:h2:mem:flow;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "travel.rag.query-rewrite=false"
    })
@AutoConfigureMockMvc
class ConversationFlowTest {
  @MockitoBean(name = "pgVectorVectorStore")
  VectorStore vectors;

  @MockitoBean ChatModel model;
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired ConversationStore store;

  @Test
  void postReplayAndSseResumeShareOnePersistedExecution() throws Exception {
    when(vectors.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
    when(model.stream(any(Prompt.class)))
        .thenReturn(
            Flux.just(
                new ChatResponse(List.of(new Generation(new AssistantMessage("第一天深圳湾，第二天博物馆。"))))));
    var init = mvc.perform(get("/v1/conversations")).andReturn();
    String token = init.getResponse().getHeader("Set-Cookie").split(";", 2)[0].split("=", 2)[1];
    var cookie = new Cookie("travel_owner", token);
    var created =
        mvc.perform(
                post("/v1/conversations")
                    .cookie(cookie)
                    .header("X-Travel-Client", "web")
                    .contentType("application/json")
                    .content(
                        "{\"title\":\"深圳两日游\",\"mode\":\"CHAT\",\"constraints\":\"两人，1500元\"}"))
            .andExpect(status().isOk())
            .andReturn();
    String cid = json.readTree(created.getResponse().getContentAsString()).get("id").asText();
    String body = "{\"message\":\"帮我规划\",\"requestId\":\"same-request\"}";
    var sent =
        mvc.perform(
                post("/v1/conversations/" + cid + "/messages")
                    .cookie(cookie)
                    .header("X-Travel-Client", "web")
                    .contentType("application/json")
                    .content(body))
            .andExpect(status().isOk())
            .andReturn();
    String id = json.readTree(sent.getResponse().getContentAsString()).get("id").asText();
    await().atMost(Duration.ofSeconds(5)).until(() -> store.run(id).status().equals("FINISHED"));
    mvc.perform(
            post("/v1/conversations/" + cid + "/messages")
                .cookie(cookie)
                .header("X-Travel-Client", "web")
                .contentType("application/json")
                .content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(id));
    mvc.perform(get("/v1/conversations/" + cid).cookie(cookie))
        .andExpect(jsonPath("$.messages.length()").value(2))
        .andExpect(jsonPath("$.messages[1].content").value("第一天深圳湾，第二天博物馆。"));
    mvc.perform(get("/v1/runs/" + id).cookie(cookie))
        .andExpect(jsonPath("$.usage.length()").value(2));
    var streaming =
        mvc.perform(
                get("/v1/runs/" + id + "/events")
                    .cookie(cookie)
                    .param("after", String.valueOf(store.run(id).lastEvent())))
            .andExpect(request().asyncStarted())
            .andReturn();
    streaming.getAsyncResult(3000);
    mvc.perform(asyncDispatch(streaming))
        .andExpect(status().isOk())
        .andExpect(content().string(org.hamcrest.Matchers.containsString("event:done")));
    verify(model, times(1)).stream(any(Prompt.class));
    assertThat(store.history(cid)).hasSize(2);
  }
}
