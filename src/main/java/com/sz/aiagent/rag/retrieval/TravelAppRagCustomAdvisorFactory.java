package com.sz.aiagent.rag.retrieval;

import jakarta.annotation.Resource;
import org.springframework.ai.chat.client.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Component;

/**
 * 自定义 RAG 检索增强顾问工厂类
 * 负责根据传入的向量存储和状态参数，创建定制化的
 * Retrieval-Augmented Generation (RAG) 检索增强顾问（Advisor）。
 * 该顾问支持：
 * - 结合向量检索进行语义搜索
 * - 根据状态字段过滤文档
 * - 定制查询增强策略
 */
@Component
@SuppressWarnings("unused")
public class TravelAppRagCustomAdvisorFactory {

    @Resource
    private ContextualQueryAugmenter travelAppContextualQueryAugmenter;

    /**
     * 创建针对指定状态的自定义 RAG 检索增强顾问
     * @param vectorStore 向量存储，用于向量检索
     * @return 配置好的 RetrievalAugmentationAdvisor 实例
     */
    public Advisor createLoveAppRagCustomAdvisor(VectorStore vectorStore) {
        // 构建过滤条件，筛选字段 "status" 等于传入状态的文档
//        Filter.Expression expression = new FilterExpressionBuilder()
//                .eq("category", status)
//                .build();

        // 创建基于向量存储的文档检索器，带过滤条件和相似度阈值
        DocumentRetriever documentRetriever = VectorStoreDocumentRetriever.builder()
                .vectorStore(vectorStore)             // 绑定向量存储
                //.filterExpression(expression)         // 过滤状态
                .similarityThreshold(0.8)              // 相似度阈值（0~1，越大越严格）
                .topK(3)                              // 返回最相关的前3条文档
                .build();

        // 构建并返回 RAG 检索增强顾问，带上下文查询增强器
        return RetrievalAugmentationAdvisor.builder()
                .documentRetriever(documentRetriever)               // 使用定制检索器
                .queryAugmenter(travelAppContextualQueryAugmenter)  // 上下文增强器
                .order(0)
                .build();
    }
}
