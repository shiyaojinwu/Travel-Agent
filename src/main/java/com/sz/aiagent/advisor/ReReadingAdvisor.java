package com.sz.aiagent.advisor;

import org.jetbrains.annotations.NotNull;
import org.springframework.ai.chat.client.advisor.api.*;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.Map;

/**
 * 🧠 ReReadingAdvisor：自定义 Spring AI Advisor
 * ✅ 功能：用于在请求发送给大模型前，对 prompt（用户输入）进行加工/增强，提升模型推理准确性。
 * 本例通过 “再次阅读问题（Read the question again）” 提示，来强化模型对提问意图的理解。
 * 🔄 支持同步（aroundCall）与流式（aroundStream）处理。
 * @author zyh
 * @version 1.0.0
 * @date 2025/07/12
 */
@SuppressWarnings("unused")
public class ReReadingAdvisor implements CallAroundAdvisor, StreamAroundAdvisor {

    /**
     * 在请求前对 Prompt 进行改写
     * @param advisedRequest 原始请求对象
     * @return 修改后的请求对象（加入 ReReading prompt）
     * @author zyh
     * @date 2025/07/12
     */
    private AdvisedRequest before(AdvisedRequest advisedRequest) {
        // 拷贝原始的用户参数 map，防止修改原始引用
        Map<String, Object> advisedUserParams = new HashMap<>(advisedRequest.userParams());
        // 将原始输入文本作为参数保存（方便 prompt 使用）
        String PARAMS_KEY = "rereading_input_query";
        advisedUserParams.put(PARAMS_KEY, advisedRequest.userText());
        // 构建新请求：Prompt 结构如下
        //  - 原始问题
        //  - Read the question again: 原始问题
        return AdvisedRequest.from(advisedRequest)
                .userText("""
                        {%s}
                        Read the question again: {%s}
                        """.formatted(PARAMS_KEY, PARAMS_KEY))// 实际会用上面设置的参数填充模板
                .userParams(advisedUserParams)
                .build();
    }

    /**
     * 同步模式：调用前拦截处理逻辑
     * @param advisedRequest 用户请求
     * @param chain          拦截器链
     * @return 模型返回的响应结果
     * @author zyh
     * @date 2025/07/12
     */
    @NotNull
    @Override
    public AdvisedResponse aroundCall(@NotNull AdvisedRequest advisedRequest, CallAroundAdvisorChain chain) {
        // 调用链的下一个 advisor，传入改写后的请求
        return chain.nextAroundCall(this.before(advisedRequest));
    }

    /**
     * 流式响应模式：调用前拦截处理逻辑
     * @param advisedRequest 用户请求
     * @param chain          拦截器链
     * @return 模型返回的响应流（如流式生成回答）
     * @author zyh
     * @date 2025/07/12
     */
    @NotNull
    @Override
    public Flux<AdvisedResponse> aroundStream(@NotNull AdvisedRequest advisedRequest, StreamAroundAdvisorChain chain) {
        // 依然使用改写后的请求，执行流式推理
        return chain.nextAroundStream(this.before(advisedRequest));
    }

    /**
     * 优先级顺序（数值越小优先级越高）
     * @return int
     * @author zyh
     * @date 2025/07/12
     */
    @Override
    public int getOrder() {
        return 2;
    }

    /**
     * Advisor 的名称，用于日志或框架内部标识
     * @return {@code String } 返回类名
     * @author zyh
     * @date 2025/07/12
     */
    @NotNull
    @Override
    public String getName() {
        return this.getClass().getSimpleName();
    }
}
