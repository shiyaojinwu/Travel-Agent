package com.sz.aiagent.rag.retrieval;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.rag.DashScopeDocumentRetriever;
import com.alibaba.cloud.ai.dashscope.rag.DashScopeDocumentRetrieverOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 游玩大师基于阿里云 DashScope 云向量知识库的 RAG 配置类 - 使用 DashScope 提供的云知识库（向量索引）服务作为检索源； - 将其封装为 Spring AI 框架所需的
 * RetrievalAugmentationAdvisor 顾问； - 支持在对话中自动调用，提升问答准确性和上下文感知能力。
 *
 * @author zyh
 * @version 1.0.0
 * @date 2025/07/13
 */
@Configuration
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
    name = "travel.rag.cloud-enabled",
    havingValue = "true")
@Slf4j
public class TravelAppRagCloudAdvisorConfig {

  /** 阿里云 DashScope 的 API Key（在配置文件中配置） */
  @Value("${spring.ai.dashscope.api-key}")
  private String dashScopeApiKey;

  /**
   * 构建并注册 Spring AI 所需的 Advisor 实例： 该 Advisor 会在 ChatClient.prompt() 调用链中，自动执行知识检索 → 提供文档上下文 → 增强
   * AI 回复。
   *
   * @return Advisor 顾问对象，集成 DashScope 检索器
   * @author zyh
   * @date 2025/07/13
   */
  @Bean
  public Advisor travelAppRagCloudAdvisor() {
    // 构建阿里云 DashScope API 客户端（使用 API Key）
    DashScopeApi dashScopeApi = new DashScopeApi(dashScopeApiKey);
    // 设置阿里云 DashScope 上配置的云知识库索引名称
    final String KNOWLEDGE_INDEX = "游玩大师";
    // 建基于 DashScope 的文档检索器（负责向量检索）
    DocumentRetriever dashScopeDocumentRetriever =
        new DashScopeDocumentRetriever(
            dashScopeApi,
            DashScopeDocumentRetrieverOptions.builder()
                .withIndexName(KNOWLEDGE_INDEX) // 指定索引名
                .build());
    // 生成Advisor方便文档检索器接入 Spring AI 的 RAG 顾问体系
    return RetrievalAugmentationAdvisor.builder()
        .documentRetriever(dashScopeDocumentRetriever)
        .build();
  }
}
