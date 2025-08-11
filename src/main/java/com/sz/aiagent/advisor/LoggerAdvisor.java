package com.sz.aiagent.advisor;

import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.ai.chat.client.advisor.api.*;
import org.springframework.ai.chat.model.MessageAggregator;
import reactor.core.publisher.Flux;

/**
 * 🧾 MyLoggerAdvisor：自定义日志拦截器（Advisor） ✅ 功能：用于在 Spring AI 执行模型调用前后，打印用户输入与模型响应的日志 🔍 优点：便于开发调试时观测
 * Prompt 和 AI 输出内容 CallAroundAdvisor：拦截同步调用 StreamAroundAdvisor：拦截流式调用（Streaming）
 *
 * @author zyh
 * @version 1.0.0
 * @date 2025/07/12
 */
@Slf4j
public class LoggerAdvisor implements CallAroundAdvisor, StreamAroundAdvisor {

  /**
   * Advisor 的名称，用于日志或框架内部标识
   *
   * @return {@code String } 返回类名
   * @author zyh
   * @date 2025/07/12
   */
  @NotNull
  @Override
  public String getName() {
    return this.getClass().getSimpleName();
  }

  /**
   * 优先级顺序（数值越小优先级越高）
   *
   * @return int
   * @author zyh
   * @date 2025/07/12
   */
  @Override
  public int getOrder() {
    return 1;
  }

  /**
   * 前置操作：记录用户输入（prompt）
   *
   * @param request 用户请求（AdvisedRequest）
   * @return 原样返回（或改写）后的请求
   * @author zyh
   * @date 2025/07/12
   */
  private AdvisedRequest before(AdvisedRequest request) {
    log.debug("AI request chars: {}", request.userText() == null ? 0 : request.userText().length());
    return request;
  }

  /**
   * 后置操作：记录模型返回结果（只输出 text 字段）
   *
   * @param advisedResponse AI 的响应封装对象
   * @author zyh
   * @date 2025/07/12
   */
  private void observeAfter(AdvisedResponse advisedResponse) {
    // 只记录文本部分（不记录 token、metadata 等）
    log.debug("AI response received");
  }

  /**
   * 同步调用时的拦截逻辑
   *
   * @param advisedRequest 用户请求
   * @param chain 调用链
   * @return AI 返回的响应
   * @author zyh
   * @date 2025/07/12
   */
  @NotNull
  @Override
  public AdvisedResponse aroundCall(
      @NotNull AdvisedRequest advisedRequest, CallAroundAdvisorChain chain) {
    // 记录请求并执行模型调用
    AdvisedResponse advisedResponse = chain.nextAroundCall(before(advisedRequest));
    // 记录模型返回结果（只输出 text 字段）
    observeAfter(advisedResponse);
    return advisedResponse;
  }

  /**
   * 流式调用（Streaming）时的拦截逻辑
   *
   * @param advisedRequest 用户请求
   * @param chain 调用链
   * @return 响应的流（Flux）
   * @author zyh
   * @date 2025/07/12
   */
  @NotNull
  @Override
  public Flux<AdvisedResponse> aroundStream(
      @NotNull AdvisedRequest advisedRequest, StreamAroundAdvisorChain chain) {
    // 打印请求并获取响应流
    Flux<AdvisedResponse> advisedResponses = chain.nextAroundStream(before(advisedRequest));
    // 对整个 Flux<AdvisedResponse> 聚合为最终一个响应，使用 this::observeAfter 作为回调函数处理日志
    return new MessageAggregator().aggregateAdvisedResponse(advisedResponses, this::observeAfter);
  }
}
