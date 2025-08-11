package com.sz.aiagent;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.sz.aiagent.app.TravelApp;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import reactor.core.publisher.Flux;

@SpringBootTest(
    properties = {
      "spring.ai.dashscope.api-key=test-only-no-network",
      "travel.legacy-api-enabled=true",
      "spring.datasource.url=jdbc:h2:mem:legacy;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "spring.ai.mcp.client.enabled=false",
      "travel.rag.import-on-startup=false"
    })
@AutoConfigureMockMvc
class HttpLifecycleTest {
  @MockitoBean(name = "pgVectorVectorStore")
  VectorStore vectorStore;

  @MockitoBean TravelApp app;
  @Autowired MockMvc mvc;

  @Test
  void rejectsEmptyInputBeforeCallingModel() throws Exception {
    mvc.perform(get("/ai/chat/sync").param("message", " ").param("chatId", "chat-1"))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(app);
  }

  @Test
  void namespacesMemoryByBrowserSession() throws Exception {
    when(app.doChat(anyString(), anyString())).thenReturn("ok");
    var first = new MockHttpSession();
    var second = new MockHttpSession();
    mvc.perform(get("/ai/chat/sync").session(first).param("message", "你好").param("chatId", "same"))
        .andExpect(status().isOk());
    mvc.perform(get("/ai/chat/sync").session(second).param("message", "你好").param("chatId", "same"))
        .andExpect(status().isOk());
    verify(app).doChat("你好", first.getId() + ":same");
    verify(app).doChat("你好", second.getId() + ":same");
  }

  @Test
  void stopDisposesUpstreamButAnotherSessionCannotStopIt() throws Exception {
    var cancelled = new AtomicBoolean();
    when(app.doChatByStream(anyString(), anyString()))
        .thenReturn(Flux.<String>never().doOnCancel(() -> cancelled.set(true)));
    var owner = new MockHttpSession();
    mvc.perform(
            get("/ai/chat/sse_emitter")
                .session(owner)
                .param("message", "你好")
                .param("chatId", "chat-1")
                .param("runId", "run-1"))
        .andExpect(request().asyncStarted());
    mvc.perform(delete("/ai/runs/run-1").session(new MockHttpSession()))
        .andExpect(status().isNoContent());
    assertThat(cancelled).isFalse();
    mvc.perform(delete("/ai/runs/run-1").session(owner)).andExpect(status().isNoContent());
    assertThat(cancelled).isTrue();
  }
}
