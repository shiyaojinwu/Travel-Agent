package com.sz.aiagent.config;

import com.sz.aiagent.advisor.LoggerAdvisor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemory;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ChatClient 配置类，用于创建并注入支持对话记忆、工具调用、Advisor 增强等功能的 ChatClient 实例。
 * 可统一管理 ChatMemory、模型适配器、工具系统与默认 System Prompt。
 * @author zyh
 * @version 1.0.0
 * @date 2025/7/13
 */
@Configuration
public class ChatClientConfig {

    /**
     * 系统提示词
     */
    public static final String SYSTEM_PROMPT = "你是逸游 TravelApp 的智能出行助手，专为用户提供高效、个性化的出行信息与行程建议。" +
            "请向用户表明身份，并告知可随时咨询景点推荐、路线规划、交通方案等问题。" +
            "支持多轮对话，能基于用户输入提供针对性的游玩方案，并结合自定义知识库进行智能问答。" +
            "你还可主动调用地图服务（如查看附近景点、餐饮、交通）、行程生成器等工具或 MCP 服务，为用户制定合理的出游计划。" +
            "请引导用户明确出行目的、时间、地点、人数、预算、兴趣偏好等要素，以便生成精准、实用的出行建议。";

    /**
     * 构造并注入对话记忆管理组件（ChatMemory）
     * 可选择基于文件持久化或内存临时存储，影响模型是否具备“记住上文”的能力。
     * @return {@code ChatMemory }
     * @author zyh
     * @date 2025/07/13
     */
    @Bean
    public ChatMemory chatMemory() {
        // 初始化基于文件的对话记忆
        // return new FileBasedChatMemory(FileConstant.FILE_SAVE_DIR);
        // 初始化基于内存的对话记忆
        return new InMemoryChatMemory();
    }

    /**
     * 构造并注入 ChatClient，配置默认的系统提示词、对话增强器（Advisor）和可选工具提供器。
     * 支持多轮对话、记忆回放、日志跟踪等功能。
     * @param dashscopeChatModel   接入的大语言模型实例（如通义千问等）
     * @param chatMemory           对话记忆模块
     * @param toolCallbackProvider 工具调用适配器，支持 LLM 主动调用工具（如地图、天气）
     * @return ChatClient 实例，作为对外聊天核心入口
     * @author zyh
     * @date 2025/07/13
     */
    @Bean
    public ChatClient chatClient(ChatModel dashscopeChatModel,
                                 ChatMemory chatMemory,
                                 ToolCallbackProvider toolCallbackProvider) {
        return ChatClient.builder(dashscopeChatModel)
                .defaultSystem(SYSTEM_PROMPT)
                .defaultAdvisors(
                        new MessageChatMemoryAdvisor(chatMemory),
                        // 自定义日志 Advisor，可按需开启
                        new LoggerAdvisor()
                        // 自定义推理增强 Advisor，可按需开启
                        // new ReReadingAdvisor()
                )
                // 若你希望默认注册工具，也可添加 .tools(toolCallbackProvider)
                .build();
    }
}
