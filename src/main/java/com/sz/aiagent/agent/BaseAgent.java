package com.sz.aiagent.agent;

import cn.hutool.core.util.StrUtil;
import com.sz.aiagent.agent.model.AgentState;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 抽象基础代理类 BaseAgent，用于定义智能体的通用结构与行为：
 * 管理智能体的生命周期状态（空闲、运行、结束等）
 * 封装了基于多轮步骤执行的循环逻辑
 * 支持同步与 SSE 异步运行两种模式
 * 管理提示词、历史消息等上下文内容
 * 子类必须实现 step() 方法来定义每一步的具体执行逻辑
 * @author zyh
 * @version 1.0.0
 * @date 2025/07/15
 */
@Data
@Slf4j
public abstract class BaseAgent {


    /**
     * 智能体名称
     */
    private String name;


    /**
     * 系统初始提示词
     */
    private String systemPrompt;

    /**
     * 下一步提示词
     */
    private String nextStepPrompt;

    /**
     * 当前代理状态（空闲 / 运行中 / 结束 / 异常）
     */
    private AgentState state = AgentState.IDLE;

    /**
     * 当前已执行的步骤数
     */
    private int currentStep = 0;

    /**
     * 最多允许执行多少步，超过则终止
     */
    private int maxSteps = 10;


    /**
     * 注入的大模型客户端（ChatClient）用于调用 LLM
     */
    private ChatClient chatClient;

    /**
     * 消息上下文，用于存储用户历史输入与模型响应（需要自主维护会话上下文）
     */
    private List<Message> messageList = new ArrayList<>();

    /**
     * 同步运行代理任务（阻塞执行）
     * @param userPrompt 用户提供的初始问题或指令
     * @return 执行结果
     * @author zyh
     * @date 2025/07/15
     */
    public String run(String userPrompt) {
        // 1、状态与参数校验
        if (this.state != AgentState.IDLE) {
            throw new RuntimeException("当前状态不可运行: " + this.state);
        }
        if (StrUtil.isBlank(userPrompt)) {
            throw new RuntimeException("用户输入不能为空");
        }
        // 2、修改状态为运行中，保存用户输入
        this.state = AgentState.RUNNING;
        messageList.add(new UserMessage(userPrompt));
        // 初始化保存结果列表
        List<String> results = new ArrayList<>();
        try {
            //  开始循环执行 step，最多执行 maxSteps 次
            for (int i = 0; i < maxSteps && state != AgentState.FINISHED; i++) {
                int stepNumber = i + 1;
                currentStep = stepNumber;
                log.info("执行步骤 {}/{}", stepNumber, maxSteps);
                // 执行单步逻辑（由子类实现）
                String stepResult = step();
                String result = "步骤 " + stepNumber + ": " + stepResult;
                results.add(result);
            }
            // 检查是否超出步骤限制
            if (currentStep >= maxSteps) {
                state = AgentState.FINISHED;
                results.add("已终止：达到最大执行步数(" + maxSteps + ")");
            }
            return String.join("\n", results);
        } catch (Exception e) {
            state = AgentState.ERROR;
            log.error("执行过程中发生错误", e);
            return "执行错误" + e.getMessage();
        } finally {
            // 3、清理资源
            this.cleanup();
        }
    }

    /**
     * 支持 SSE（服务端推送）方式的异步执行
     * @param userPrompt 用户提示词
     * @return 执行结果
     * @author zyh
     * @date 2025/07/15
     */
    public SseEmitter runStream(String userPrompt) {
        // 创建 SseEmitter，超时时间为 5 分钟
        SseEmitter sseEmitter = new SseEmitter(300000L);
        // 异步线程执行主流程，防止阻塞主线程
        CompletableFuture.runAsync(() -> {
            // 1、基础校验
            try {
                if (this.state != AgentState.IDLE) {
                    sseEmitter.send("错误：无法从当前状态运行代理：" + this.state);
                    sseEmitter.complete();
                    return;
                }
                if (StrUtil.isBlank(userPrompt)) {
                    sseEmitter.send("错误：用户输入不能为空");
                    sseEmitter.complete();
                    return;
                }
            } catch (Exception e) {
                sseEmitter.completeWithError(e);
            }
            // 2、执行，更改状态
            this.state = AgentState.RUNNING;
            // 记录消息上下文
            messageList.add(new UserMessage(userPrompt));
            // 保存结果列表
            List<String> results = new ArrayList<>();
            try {
                // 执行循环
                for (int i = 0; i < maxSteps && state != AgentState.FINISHED; i++) {
                    int stepNumber = i + 1;
                    currentStep = stepNumber;
                    log.info("执行步骤 {}/{}", stepNumber, maxSteps);
                    // 单步执行
                    String stepResult = step();
                    String result = "步骤 " + stepNumber + ": " + stepResult;
                    results.add(result);
                    // 将当前结果通过 SSE 发送到前端
                    sseEmitter.send(result);
                }
                // 检查是否超出步骤限制
                if (currentStep >= maxSteps) {
                    state = AgentState.FINISHED;
                    results.add("执行完毕：已达到最大步骤数 (" + maxSteps + ")");
                    sseEmitter.send("执行结束：达到最大步骤（" + maxSteps + "）");
                }
                // 正常完成
                sseEmitter.complete();
            } catch (Exception e) {
                state = AgentState.ERROR;
                log.error("执行过程中发生错误", e);
                try {
                    sseEmitter.send("执行错误：" + e.getMessage());
                    sseEmitter.complete();
                } catch (IOException ex) {
                    sseEmitter.completeWithError(ex);
                }
            } finally {
                // 3、清理资源
                this.cleanup();
            }
        });

        // 设置超时回调
        sseEmitter.onTimeout(() -> {
            this.state = AgentState.ERROR;
            this.cleanup();
            log.warn("SSE 连接超时");
        });
        // 设置完成回调
        sseEmitter.onCompletion(() -> {
            if (this.state == AgentState.RUNNING) {
                this.state = AgentState.FINISHED;
            }
            this.cleanup();
            log.info("SSE 连接完成");
        });
        return sseEmitter;
    }

    /**
     * 定义单个步骤
     * @return {@code String }
     * @author zyh
     * @date 2025/07/15
     */
    public abstract String step();

    /**
     * 清理资源，子类可以重写此方法来清理资源
     * @author zyh
     * @date 2025/07/15
     */
    protected void cleanup() {
    }
}
