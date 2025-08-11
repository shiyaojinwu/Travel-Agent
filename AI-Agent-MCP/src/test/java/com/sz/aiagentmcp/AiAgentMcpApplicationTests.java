package com.sz.aiagentmcp;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@org.junit.jupiter.api.Tag("integration")
@SpringBootTest
class AiAgentMcpApplicationTests {
  @Resource private ImageSearchTool imageSearchTool;

  @Test
  void searchImage() {
    String result = imageSearchTool.searchImage("computer");
    System.out.println(result);
    Assertions.assertNotNull(result);
  }
}
