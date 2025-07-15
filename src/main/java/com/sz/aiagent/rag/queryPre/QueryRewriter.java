package com.sz.aiagent.rag.queryPre;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.preretrieval.query.transformation.QueryTransformer;
import org.springframework.ai.rag.preretrieval.query.transformation.RewriteQueryTransformer;
import org.springframework.stereotype.Component;

/**
 * 查询重写器组件
 * 负责在 RAG 检索前对用户的查询语句进行重写优化，
 * 利用底层 ChatClient 的能力改写查询，提升检索效果。
 * @author zyh
 * @version 1.0.0
 * @date 2025/07/13
 */
@Component
@SuppressWarnings("unused")
public class QueryRewriter {

    private final QueryTransformer queryTransformer;

    /**
     * 通过 ChatModel 构造 QueryTransformer 实例
     * @param dashscopeChatModel 注入的聊天模型，供重写器使用
     * @author zyh
     * @date 2025/07/13
     */
    public QueryRewriter(ChatModel dashscopeChatModel) {
        // 构建 ChatClient.Builder，用于 RewriteQueryTransformer 调用
        ChatClient.Builder builder = ChatClient.builder(dashscopeChatModel);
        // 创建重写查询转换器
        this.queryTransformer = RewriteQueryTransformer.builder()
                .chatClientBuilder(builder)
                .build();
    }

    /**
     * 执行查询重写
     * @param prompt 原始查询文本
     * @return 重写后的查询文本
     * @author zyh
     * @date 2025/07/13
     */
    public String doQueryRewrite(String prompt) {
        Query query = new Query(prompt);
        // 使用转换器重写查询
        Query transformedQuery = queryTransformer.transform(query);
        // 返回重写后的文本
        return transformedQuery.text();
    }
}
