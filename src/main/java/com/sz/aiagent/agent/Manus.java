package com.sz.aiagent.agent;

import com.sz.aiagent.advisor.LoggerAdvisor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.stereotype.Component;

/**
 * 一个拥有自主规划能力的 AI 超级智能体，能够根据用户需求选择合适工具完成复杂任务。
 * 继承自 ToolCallAgent，具备多轮决策与工具调用能力。
 * @author zyh
 * @version 1.0.0
 * @date 2025/07/15
 */
@Component
public class Manus extends ToolCallAgent {

    /**
     * 构造方法：初始化 Manus 智能体
     * @param allTools           所有可用工具（ToolCallback 的数组）
     * @param dashscopeChatModel 当前使用的语言大模型（如 DashScope）
     * @author zyh
     * @date 2025/07/15
     */
    public Manus(ToolCallback[] allTools, ChatModel dashscopeChatModel, ToolCallbackProvider toolCallbackProvider) {
        // 注册工具
        super(allTools,toolCallbackProvider);
        // 设置智能体名称
        this.setName("Manus");
        // 🧭 系统提示词：定义智能体的角色与能力
        String SYSTEM_PROMPT = """
                You are Manus, an all-capable AI assistant, aimed at solving any task presented by the user.
                You have various tools at your disposal that you can call upon to efficiently complete complex requests.
                Proactively select the most appropriate tool or combination of tools according to the user's needs.
                For simple tasks, you'll need to answer the user in a response and then use the 'terminate' tool/function call to stop the action in a timely manner.
                For complex tasks, you can break down the problem and use different tools to solve it step by step.
                After using each tool, clearly explain the results of the execution and suggest next steps.
                If you want to stop the interaction at any point, use the 'terminate' tool/function call.
                """;
        this.setSystemPrompt(SYSTEM_PROMPT);
        // 🔁 下一步提示词：指引智能体如何逐步解决问题、规划任务流程
        String NEXT_STEP_PROMPT = """
                Proactively select the most appropriate tool or combination of tools according to the user's needs.
                For simple tasks, you'll need to answer the user in a response and then use the 'terminate' tool/function call to stop the action in a timely manner.
                For complex tasks, you can break down the problem and use different tools to solve it step by step.
                After using each tool, clearly explain the results of the execution and suggest next steps.
                If you want to stop the interaction at any point, use the 'terminate' tool/function call.
                """;
        this.setNextStepPrompt(NEXT_STEP_PROMPT);
        // 设置最多执行的步骤次数，避免无限循环
        this.setMaxSteps(10);
        // ⚙️ 初始化 ChatClient 客户端，绑定模型与默认 Advisor（如日志记录）
        ChatClient chatClient = ChatClient.builder(dashscopeChatModel)
                .defaultAdvisors(new LoggerAdvisor())
                .build();
        // 设置对话客户端，支持消息收发与模型调用
        this.setChatClient(chatClient);
    }
}
