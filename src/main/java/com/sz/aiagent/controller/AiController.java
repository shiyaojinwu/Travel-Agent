package com.sz.aiagent.controller;

import com.sz.aiagent.agent.Manus;
import com.sz.aiagent.app.TravelApp;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.io.IOException;

/**
 * AI 服务控制器
 * 提供多种方式调用 AI 应用和 Manus 超级智能体的接口，
 * 支持同步调用、SSE 流式推送、以及基于 SseEmitter 的推送。
 */
@RestController
@RequestMapping("/ai")
public class AiController {

    /**
     * 注入旅行应用核心服务
     */
    @Resource
    private TravelApp travelApp;

    /**
     * 注入所有工具回调，供 Manus 智能体调用
     */
    @Resource
    private ToolCallback[] allTools;

    /**
     * 注入 DashScope ChatModel，用于与 AI 交互
     */
    @Resource
    private ChatModel dashscopeChatModel;

    /**
     * 同步调用 AI 应用
     * 接收用户消息，返回 AI 回复的完整文本
     * @param message 用户输入消息
     * @param chatId  会话 ID，用于维护上下文
     * @return AI 回复的文本结果
     * @author zyh
     * @date 2025/07/13
     */
    @GetMapping("/love_app/chat/sync")
    public String doChatWithLoveAppSync(String message, String chatId) {
        return travelApp.doChat(message, chatId);
    }

    /**
     * SSE 流式调用 AI 应用
     * 返回一个 Flux 数据流，逐步推送 AI 回复的内容片段（字符串）
     * @param message 用户输入消息
     * @param chatId  会话 ID
     * @return Flux 流，逐步返回 AI 回复文本片段
     * @author zyh
     * @date 2025/07/13
     */
    @GetMapping(value = "/love_app/chat/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> doChatWithLoveAppSSE(String message, String chatId) {
        return travelApp.doChatByStream(message, chatId);
    }

    /**
     * SSE 流式调用，包装 Flux 为 ServerSentEvent 格式
     * 便于客户端以 SSE 协议接收分片数据
     * @param message 用户输入消息
     * @param chatId  会话 ID
     * @return Flux<ServerSentEvent>，推送 AI 回复事件流
     * @author zyh
     * @date 2025/07/13
     */
    @GetMapping(value = "/love_app/chat/server_sent_event")
    public Flux<ServerSentEvent<String>> doChatWithLoveAppServerSentEvent(String message, String chatId) {
        return travelApp.doChatByStream(message, chatId)
                .map(chunk -> ServerSentEvent.<String>builder()
                        .data(chunk)
                        .build());
    }

    /**
     * 使用 Spring MVC 的 SseEmitter 实现 SSE 推送
     * 手动订阅 Flux 并推送数据给客户端
     * @param message 用户输入消息
     * @param chatId  会话 ID
     * @return SseEmitter，支持长连接推送数据
     * @author zyh
     * @date 2025/07/13
     */
    @GetMapping(value = "/love_app/chat/sse_emitter")
    public SseEmitter doChatWithLoveAppServerSseEmitter(String message, String chatId) {
        // 创建超时为 3 分钟的 SseEmitter 实例
        SseEmitter sseEmitter = new SseEmitter(180000L);
        // 订阅 AI 流式回复，将每个片段发送到客户端
        travelApp.doChatByStream(message, chatId)
                .subscribe(chunk -> {
                            try {
                                sseEmitter.send(chunk);
                            } catch (IOException e) {
                                sseEmitter.completeWithError(e);
                            }
                        },
                        sseEmitter::completeWithError,
                        sseEmitter::complete);
        return sseEmitter;
    }

    /**
     * 流式调用 Manus 超级智能体
     * 使用所有注册的工具和 ChatModel，开启智能体对话流
     * @param message 用户输入消息
     * @return SseEmitter，推送 Manus 智能体的回复流
     * @author zyh
     * @date 2025/07/13
     */
    @GetMapping("/manus/chat")
    public SseEmitter doChatWithManus(String message) {
        // 新建 Manus 智能体实例（无状态）
        Manus Manus = new Manus(allTools, dashscopeChatModel);
        // 运行并返回流式响应的 SseEmitter
        return Manus.runStream(message);
    }
}
