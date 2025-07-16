package com.sz.aiagent.app;

import com.sz.aiagent.advisor.LoggerAdvisor;
import com.sz.aiagent.config.ChatClientConfig;
import com.sz.aiagent.rag.queryPre.QueryRewriter;
import com.sz.aiagent.rag.retrieval.TravelAppRagCustomAdvisorFactory;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.QuestionAnswerAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor.CHAT_MEMORY_CONVERSATION_ID_KEY;
import static org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor.CHAT_MEMORY_RETRIEVE_SIZE_KEY;

/**
 * 提供了一个与聊天机器人进行旅行相关对话的功能
 * 它使用了 ChatClient 来与后端的聊天服务进行交互，并支持多轮对话的记忆功能
 * @author zyh
 * @date 2025/07/13
 */
@Component
@Slf4j
public class TravelApp {

    private final ChatClient chatClient;

    /**
     * 构造函数，通过构造方法注入 ChatClient 实例
     * @param chatClient 聊天客户端，用于发起与大语言模型的对话
     */
    public TravelApp(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    /**
     * AI 基础对话（支持多轮对话记忆）
     * 示例功能：
     * - 自动调用系统提示词（System Prompt）
     * - 自动挂载默认 advisors（如记忆管理、日志记录等）
     * - 支持多轮对话上下文恢复（通过 chatId 关联记忆）
     * @param message 用户消息
     * @param chatId  会话 ID
     * @author zyh
     * @date 2025/07/13
     */
    public String doChat(String message, String chatId) {
        ChatResponse chatResponse = chatClient
                // 创建一次新的对话请求（Prompt）
                .prompt()
                // 设置用户输入内容
                .user(message)
                // 设置本次会话的参数，包括：
                // - chatId: 关联上下文记忆
                // - retrieveSize: 设置回溯历史消息的条数（控制“记忆长度”）
                .advisors(spec -> spec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId)
                        .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 10))
                // 发送请求并获取响应
                .call()
                .chatResponse();
        String content = chatResponse.getResult().getOutput().getText();
        log.info("AI Response content: {}", content);
        return content;
    }

    /**
     * AI 基础对话（支持多轮对话记忆，SSE 流式传输）
     * @param message 用户消息
     * @param chatId  会话 ID
     * @return {@code Flux<String> }
     * @author zyh
     * @date 2025/07/13
     */
    public Flux<String>  doChatByStream(String message, String chatId) {
        return chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId)
                        .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 10))
                // RAG
                .advisors(travelAppRagCustomAdvisorFactory
                        .createLoveAppRagCustomAdvisor(pgVectorVectorStore))
                .stream()
                .content();
    }
    public record LoveReport(String title, List<String> suggestions) {

    }

    /**
     * AI 恋爱报告功能（结构化输出）
     * @param message 用户消息
     * @param chatId  会话 ID
     * @return {@code LoveReport }
     * @author zyh
     * @date 2025/07/13
     */
    public LoveReport doChatWithReport(String message, String chatId) {
        LoveReport loveReport = chatClient
                .prompt()
                .system(ChatClientConfig.SYSTEM_PROMPT + "每次对话后都要生成出行游玩结果，标题为{用户名}的游玩报告，内容为建议列表")
                .user(message)
                .advisors(spec -> spec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId)
                        .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 10))
                .call()
                .entity(LoveReport.class);
        log.info("loveReport: {}", loveReport);
        return loveReport;
    }
    // AI 恋爱知识库问答功能

    @Resource
    @Qualifier("travelAppVectorStore")
    private VectorStore travelAppVectorStore;
    @Resource
    private TravelAppRagCustomAdvisorFactory travelAppRagCustomAdvisorFactory;
    @Resource
    private Advisor travelAppRagCloudAdvisor;

    @Resource
    @Qualifier("pgVectorVectorStore")
    private VectorStore pgVectorVectorStore;

    @Resource
    private QueryRewriter queryRewriter;

    /**
     * 和 RAG 知识库进行对话
     * @param message 用户消息
     * @param chatId  会话 ID
     * @return {@code String }
     * @author zyh
     * @date 2025/07/13
     */
    public String doChatWithRag(String message, String chatId) {
        ChatResponse chatResponse = chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId)
                        .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 10))
                // 应用 RAG 知识库问答（基于内存）
                //.advisors(new QuestionAnswerAdvisor(travelAppVectorStore))
                // 应用 RAG 检索增强服务（基于云知识库服务）
                //.advisors(travelAppRagCloudAdvisor)
                // 应用 RAG 检索增强服务（基于 PgVector 向量存储）
                //.advisors(new QuestionAnswerAdvisor(pgVectorVectorStore))
                // 应用自定义的 RAG 检索增强服务（文档查询器 + 上下文增强器）
                .advisors(travelAppRagCustomAdvisorFactory
                                .createLoveAppRagCustomAdvisor(pgVectorVectorStore))
                .call()
                .chatResponse();
        String content = chatResponse.getResult().getOutput().getText();
        log.info("content: {}", content);
        return content;
    }

    // AI 调用工具能力
    @Resource
    private ToolCallback[] allTools;

    /**
     * AI 游玩报告功能（支持调用工具）
     * @param message 用户消息
     * @param chatId  会话 ID
     * @return {@code String }
     * @author zyh
     * @date 2025/07/13
     */
    public String doChatWithTools(String message, String chatId) {
        ChatResponse chatResponse = chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId)
                        .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 10))
                .tools(allTools)
                .call()
                .chatResponse();
        String content = chatResponse.getResult().getOutput().getText();
        log.info("content: {}", content);
        return content;
    }

    // AI 调用 MCP 服务

    @Resource
    private ToolCallbackProvider toolCallbackProvider;

    /**
     * AI 恋爱报告功能（调用 MCP 服务）
     * @param message 用户消息
     * @param chatId  会话 ID
     * @return {@code String }
     * @author zyh
     * @date 2025/07/13
     */
    public String doChatWithMcp(String message, String chatId) {
        ChatResponse chatResponse = chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId)
                        .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 10))
                .tools(toolCallbackProvider)
                .call()
                .chatResponse();
        String content = chatResponse.getResult().getOutput().getText();
        log.info("content: {}", content);
        return content;
    }
}
