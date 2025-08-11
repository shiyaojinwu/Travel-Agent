package com.sz.aiagent.tools.registration;

import com.sz.aiagent.tools.TerminateTool;
import com.sz.aiagent.tools.WebSearchTool;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbacks;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Only travel-search capabilities are exposed. Legacy local-machine tools remain unregistered. */
@Configuration
public class ToolRegistration {
  @Bean
  public ToolCallback[] allTools(
      WebSearchTool search,
      TerminateTool terminate,
      @Value("${search-api.api-key:}") String searchKey) {
    return searchKey.isBlank()
        ? ToolCallbacks.from(terminate)
        : ToolCallbacks.from(search, terminate);
  }
}
