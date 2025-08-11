package com.sz.aiagent.conversation;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sz.aiagent.conversation.application.*;
import com.sz.aiagent.conversation.domain.ApiFailure;
import com.sz.aiagent.conversation.infrastructure.ConversationStore;
import jakarta.servlet.http.Cookie;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    properties = {"spring.ai.dashscope.api-key=offline", "spring.ai.mcp.client.enabled=false"})
@AutoConfigureMockMvc
class ConversationPersistenceTest {
  @DynamicPropertySource
  static void db(DynamicPropertyRegistry r) {
    r.add(
        "spring.datasource.url",
        () ->
            System.getenv()
                .getOrDefault(
                    "TEST_DB_URL",
                    "jdbc:h2:mem:conversations;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"));
    r.add("spring.datasource.username", () -> System.getenv().getOrDefault("TEST_DB_USER", "sa"));
    r.add("spring.datasource.password", () -> System.getenv().getOrDefault("TEST_DB_PASSWORD", ""));
  }

  @MockitoBean(name = "pgVectorVectorStore")
  VectorStore vectors;

  @MockitoBean ConversationEngine engine;
  @Autowired ConversationStore store;
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  String owner, cid;

  @BeforeEach
  void setup() {
    owner = UUID.randomUUID().toString();
    store.createOwner(owner);
    cid = store.create(owner, "深圳旅行", "CHAT", "预算 1500").id();
  }

  ConversationStore.Started start(String key, String text) {
    return store.start(owner, cid, key, text, "test", "prompt-v1", "v1", "readonly", 40);
  }

  @Test
  void concurrentDuplicateCreatesOneRunAndMessage() throws Exception {
    try (var pool = Executors.newFixedThreadPool(4)) {
      List<Callable<ConversationStore.Started>> calls =
          java.util.stream.IntStream.range(0, 4)
              .mapToObj(i -> (Callable<ConversationStore.Started>) () -> start("same", "出发"))
              .toList();
      var results =
          pool.invokeAll(calls).stream()
              .map(
                  f -> {
                    try {
                      return f.get();
                    } catch (Exception e) {
                      throw new RuntimeException(e);
                    }
                  })
              .toList();
      assertThat(results.stream().filter(ConversationStore.Started::created).count()).isEqualTo(1);
      assertThat(results.stream().map(x -> x.run().id()).distinct().count()).isEqualTo(1);
      assertThat(store.history(cid)).hasSize(1);
      store.finish(results.getFirst().run().id(), "FINISHED", null, "计划");
      assertThat(start("same", "出发").created()).isFalse();
      assertThat(store.history(cid)).hasSize(2);
      assertThatThrownBy(() -> start("same", "不同内容"))
          .isInstanceOf(ApiFailure.class)
          .hasMessageContaining("不同消息");
    }
  }

  @Test
  void cancellationRetainsPartialAndIgnoresLateWrites() {
    var run = start("cancel", "hello").run();
    store.claim(run.id());
    store.event(run.id(), "delta", Map.of("content", "已收到内容"));
    store.finish(run.id(), "CANCELLED", "CANCELLED", null);
    store.event(run.id(), "delta", Map.of("content", "迟到内容"));
    store.finish(run.id(), "FINISHED", null, "迟到答案");
    assertThat(store.history(cid).getLast().content()).isEqualTo("已收到内容");
    assertThat(store.run(run.id()).status()).isEqualTo("CANCELLED");
    assertThat(store.events(run.id(), 1))
        .extracting(ConversationStore.Event::type)
        .containsExactly("done");
  }

  @Test
  void recoveryKeepsHistoryAndDoesNotReplayTasks() {
    var run = start("restart", "旅行").run();
    store.event(run.id(), "delta", Map.of("content", "半份计划"));
    store.recover();
    assertThat(store.run(run.id()).status()).isEqualTo("INTERRUPTED");
    assertThat(store.history(cid).getLast().content()).isEqualTo("半份计划");
    assertThat(start("restart", "旅行").created()).isFalse();
  }

  @Test
  void perOwnerConcurrencyAndRateLimitsArePersistent() {
    var run = start("one", "a").run();
    assertThatThrownBy(() -> start("two", "b"))
        .isInstanceOf(ApiFailure.class)
        .hasMessageContaining("已有任务");
    store.finish(run.id(), "FINISHED", null, "a");
    assertThatThrownBy(() -> store.start(owner, cid, "two", "b", "m", "p", "v", "t", 1))
        .isInstanceOf(ApiFailure.class)
        .hasMessageContaining("每小时");
  }

  @Test
  void summaryCoverageIsMonotonicAndIndependentOfTranscript() {
    var run = start("sum", "a").run();
    store.finish(run.id(), "FINISHED", null, "b");
    long covered = store.history(cid).getLast().seq();
    store.summary(cid, covered, "预算1500");
    store.summary(cid, covered - 1, "旧摘要");
    assertThat(store.summary(cid).version()).isEqualTo(1);
    assertThat(store.summary(cid).content()).isEqualTo("预算1500");
    assertThat(store.history(cid)).hasSize(2);
  }

  @Test
  void usageLedgerPersistsBothProviderAndEstimatedCalls() {
    var run = start("usage", "旅行").run();
    store.call(run.id(), "rewrite", 30, 5, false, "FINISHED", 120L, null);
    store.call(run.id(), "answer", 100, 40, true, "FAILED", 200L, 60L);
    store.tool(run.id(), "searchWeb", "DENIED", 0L);
    assertThat(store.usedTokens(run.id())).isEqualTo(175);
    assertThat(store.usage(run.id())).hasSize(2);
    assertThat(store.tools(run.id())).hasSize(1);
    store.finish(run.id(), "FAILED", "PROVIDER_ERROR", null);
  }

  @Test
  void browserCookieOwnershipAndMutationHeaderAreEnforced() throws Exception {
    var bootstrap = mvc.perform(get("/v1/conversations")).andExpect(status().isOk()).andReturn();
    var raw = bootstrap.getResponse().getHeader("Set-Cookie");
    assertThat(raw).contains("HttpOnly", "SameSite=Lax", "Max-Age=");
    Cookie cookie = new Cookie("travel_owner", raw.split(";", 2)[0].split("=", 2)[1]);
    String body = "{\"title\":\"我的旅行\",\"mode\":\"CHAT\",\"constraints\":\"两个人\"}";
    mvc.perform(
            post("/v1/conversations").cookie(cookie).contentType("application/json").content(body))
        .andExpect(status().isForbidden());
    var result =
        mvc.perform(
                post("/v1/conversations")
                    .cookie(cookie)
                    .header("X-Travel-Client", "web")
                    .contentType("application/json")
                    .content(body))
            .andExpect(status().isOk())
            .andReturn();
    String id = json.readTree(result.getResponse().getContentAsString()).get("id").asText();
    mvc.perform(get("/v1/conversations/" + id).cookie(cookie)).andExpect(status().isOk());
    mvc.perform(get("/v1/conversations/" + id)).andExpect(status().isNotFound());
    mvc.perform(
            patch("/v1/conversations/" + id)
                .header("X-Travel-Client", "web")
                .contentType("application/json")
                .content(body))
        .andExpect(status().isNotFound());
    mvc.perform(get("/ai/chat/sync").param("message", "hello").param("chatId", "legacy"))
        .andExpect(status().isNotFound());
  }

  @Test
  void otherOwnerCannotReadRunEventsOrCancel() throws Exception {
    var run = start("protected", "hello").run();
    mvc.perform(get("/v1/runs/" + run.id())).andExpect(status().isNotFound());
    mvc.perform(get("/v1/runs/" + run.id() + "/events")).andExpect(status().isNotFound());
    mvc.perform(post("/v1/runs/" + run.id() + "/cancel").header("X-Travel-Client", "web"))
        .andExpect(status().isNotFound());
    assertThat(store.run(run.id()).status()).isEqualTo("QUEUED");
    store.finish(run.id(), "CANCELLED", "CANCELLED", null);
  }
}
