package com.sz.aiagent.agent;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@Tag("integration")
@SpringBootTest
class ManusTest {
  @Autowired ChatModel model;
  @Autowired ToolCallback[] tools;
  @Autowired ObjectProvider<ToolCallbackProvider> mcp;

  @Test
  void directTravelQuestion() {
    var agent = new Manus(tools, model, mcp.getIfAvailable());
    assertThat(agent.run("请告诉我旅行前需要准备的三件物品，无需查询外部资料。")).isNotBlank();
    assertThat(agent.getState()).isEqualTo(com.sz.aiagent.agent.model.AgentState.FINISHED);
  }
}
