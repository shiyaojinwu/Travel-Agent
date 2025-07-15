package com.sz.aiagent.rag.queryPre;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.preretrieval.query.expansion.MultiQueryExpander;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 🔍 查询扩展器示例
 * 功能：将用户的单一问题扩展为多个相似但有差异的查询，以提升 RAG 检索的召回率。
 * 使用 Spring AI 提供的 MultiQueryExpander 实现。
 * @author zyh
 * @version 1.0.0
 * @date 2025/07/13
 */
@Component
@SuppressWarnings("unused")
public class MyMultiQueryExpander {

    /**
     * 多查询扩展器
     */
    private final MultiQueryExpander queryExpander;

    /**
     * 构造函数，基于指定的 ChatModel 初始化查询扩展器
     * @param dashscopeChatModel 阿里云 DashScope 模型（或其他支持的 ChatModel）
     * @author zyh
     * @date 2025/07/13
     */
    public MyMultiQueryExpander(ChatModel dashscopeChatModel) {
        ChatClient.Builder builder = ChatClient.builder(dashscopeChatModel);
        this.queryExpander = MultiQueryExpander.builder()
                .chatClientBuilder(builder) // 使用指定模型构造 ChatClient
                .numberOfQueries(3)         // 指定扩展出 3 个查询
                .build();
    }

    /**
     * 对用户输入进行查询扩展（返回字符串列表）
     * @param prompt 用户原始查询问题
     * @return 扩展后的查询文本列表
     */
    public List<String> multiQuery(String prompt) {
        Query query = new Query(prompt);
        return queryExpander.expand(query)
                .stream()
                .map(Query::text) // 提取 Query 中的字符串
                .toList();
    }
}
