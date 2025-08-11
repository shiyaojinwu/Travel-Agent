package com.sz.aiagent.agent;

import cn.hutool.core.collection.CollUtil;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.sz.aiagent.agent.model.AgentState;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;

/**
 * @author zyh
 * @version 1.0.0
 * @date 2025/07/15
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Slf4j
public class ToolCallAgent extends ReActAgent {

  private ToolCallbackProvider toolCallbackProvider;

  /** 可用的工具集，ToolCallback 是工具的回调接口 */
  private final ToolCallback[] availableTools;

  /** 模型响应对象（含工具调用请求与助手回复） */
  private ChatResponse toolCallChatResponse;

  /** 工具调用执行管理器（用于真正执行工具逻辑） */
  private final ToolCallingManager toolCallingManager;

  /** 自定义 ChatOptions，禁用 Spring AI 的内置自动工具调用（使用代理方式）自己维护选项和消息上下文 */
  private final ChatOptions chatOptions;

  /**
   * 构造函数：初始化工具与相关配置
   *
   * @param availableTools 可用工具集
   * @author zyh
   * @date 2025/07/15
   */
  public ToolCallAgent(ToolCallback[] availableTools, ToolCallbackProvider toolCallbackProvider) {
    super();
    this.availableTools = availableTools;
    this.toolCallbackProvider = toolCallbackProvider;
    var callbacks =
        new java.util.ArrayList<org.springframework.ai.model.function.FunctionCallback>(
            java.util.Arrays.asList(availableTools));
    if (toolCallbackProvider != null)
      callbacks.addAll(java.util.Arrays.asList(toolCallbackProvider.getToolCallbacks()));
    this.toolCallingManager =
        ToolCallingManager.builder()
            .toolCallbackResolver(
                name ->
                    callbacks.stream()
                        .filter(tool -> tool.getName().equals(name))
                        .findFirst()
                        .orElse(null))
            .build();
    // 禁用 Spring AI 内置的工具调用机制，自己维护选项和消息上下文
    this.chatOptions =
        DashScopeChatOptions.builder()
            .withProxyToolCalls(true) // ✅ 不走自动执行，手动控制工具调用
            .build();
  }

  /**
   * 思考阶段：调用大模后型判断是否需要调用工具
   *
   * @return true 表示需要执行工具调用，false 表示无需调用工具
   * @author zyh
   * @date 2025/07/15
   */
  @Override
  public boolean think() {
    // 1、下一步提示词，拼接下一步提示词，每次思考都会拼接一次，不断引导
    //        if (StrUtil.isNotBlank(getNextStepPrompt())) {
    //            UserMessage userMessage = new UserMessage(getNextStepPrompt());
    //            getMessageList().add(userMessage);
    //        }
    // 2、调用 AI 大模型，获取工具调用结果
    List<Message> messageList = getMessageList();
    Prompt prompt = new Prompt(messageList, this.chatOptions);
    try {
      // 发起请求，获得模型响应
      var request = getChatClient().prompt(prompt).system(getSystemPrompt()).tools(availableTools);
      if (toolCallbackProvider != null) request.tools(toolCallbackProvider);
      ChatResponse chatResponse = request.call().chatResponse();
      if (chatResponse == null || chatResponse.getResult() == null) {
        throw new IllegalStateException("模型返回空响应");
      }
      // 记录响应，用于等下 Act
      this.toolCallChatResponse = chatResponse;
      // 3、解析工具调用结果，获取要调用的工具
      // 解析响应：是否包含工具调用 获取要调用的工具列表
      AssistantMessage assistantMessage = chatResponse.getResult().getOutput();
      List<AssistantMessage.ToolCall> toolCallList = assistantMessage.getToolCalls();
      log.debug("Agent selected {} tools", toolCallList.size());
      // 判断是否需要执行工具调用
      if (toolCallList.isEmpty()) {
        // 如果没有工具调用，则将助手消息加入上下文
        getMessageList().add(assistantMessage);
        return false;
      } else {
        // 有工具调用，act 中会自动处理并记录，无需添加助手消息
        return true;
      }
    } catch (Exception e) {
      throw new IllegalStateException("模型调用失败", e);
    }
  }

  /**
   * 行动阶段：执行工具调用并将结果记录进上下文
   *
   * @return 执行结果
   * @author zyh
   * @date 2025/07/15
   */
  @Override
  public String act() {
    // 1️⃣ 校验是否有工具调用需要处理
    if (!toolCallChatResponse.hasToolCalls()) {
      return "无工具调用请求，跳过执行";
    }

    // 2️⃣ 使用 ToolCallingManager 执行工具调用
    Prompt prompt = new Prompt(getMessageList(), this.chatOptions);
    ToolExecutionResult toolExecutionResult =
        toolCallingManager.executeToolCalls(prompt, toolCallChatResponse);

    // 3️⃣ 更新上下文：包含工具请求与响应消息
    setMessageList(new java.util.ArrayList<>(toolExecutionResult.conversationHistory()));

    // 4️⃣ 取出最后一条工具响应消息（类型为 ToolResponseMessage）
    ToolResponseMessage toolResponseMessage =
        (ToolResponseMessage) CollUtil.getLast(toolExecutionResult.conversationHistory());

    // 5️⃣ 检查是否调用了终止类工具（如 doTerminate）
    boolean terminateToolCalled =
        toolResponseMessage.getResponses().stream()
            .anyMatch(response -> response.name().equals("doTerminate"));
    if (terminateToolCalled) {
      setState(AgentState.FINISHED); // 更改状态为已完成
    }

    // 6️⃣ 整理工具调用的结果返回文本
    String results =
        toolResponseMessage.getResponses().stream()
            .map(response -> "工具 " + response.name() + " 返回的结果：" + response.responseData())
            .collect(Collectors.joining("\n"));

    log.debug("Tool execution completed");
    return results;
  }
}
