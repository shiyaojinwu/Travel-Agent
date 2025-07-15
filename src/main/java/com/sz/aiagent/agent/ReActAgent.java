package com.sz.aiagent.agent;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;

/**
 * ReAct (Reasoning and Acting) 模式的代理抽象类
 * 基于 ReAct 思维方式，支持“思考 → 决策 → 行动”循环
 * 在每一个 step 中先进行“思考”判断是否需要执行“行动”
 * 子类需实现 think() 与 act() 方法定义智能体的决策逻辑与行为逻辑
 * 可用于构建可中断、具备自主推理能力的智能体（如 ToolAgent、搜索代理等）
 * 继承自 BaseAgent，重写 step() 方法，实现思考 + 行动流程。
 * 示例场景：
 * - 智能体通过思考决定是否调用工具（如搜索引擎）
 * - 如果需要执行工具调用，则进入 act() 执行逻辑
 * - 否则跳过行为，仅更新状态
 * @author zyh
 * @version 1.0.0
 * @date 2025/07/15
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Slf4j
public abstract class ReActAgent extends BaseAgent {

    /**
     * 智能体进行“思考”，分析当前上下文，决定是否需要采取行动。
     * 子类需实现该方法：
     * - 可分析 messageList 上下文
     * - 决定是否调用工具或继续对话
     * @return 是否需要执行行动，true表示需要执行，false表示不需要执行
     * @author zyh
     * @date 2025/07/15
     */
    public abstract boolean think();

    /**
     * 执行决策后的“行动”
     * 子类需实现该方法：
     * - 如调用外部 API、使用工具、回复用户等操作
     * - 支持将执行结果写入上下文中（如添加 Message）
     * @return 行动执行结果
     * @author zyh
     * @date 2025/07/15
     */
    public abstract String act();

    /**
     * 单步执行逻辑：思考 → 判断是否行动 → 执行行动
     * 这是 BaseAgent 中 step() 方法的具体实现。
     * @return 步骤执行结果
     * @author zyh
     * @date 2025/07/15
     */
    @Override
    public String step() {
        try {
            // 先进行思考
            boolean shouldAct = think();
            // 不需要行动，直接返回提示
            if (!shouldAct) {
                return "✅ 思考完成 - 无需进一步行动";
            }
            // 需要行动，调用 act 方法
            return act();
        } catch (Exception e) {
            // 捕获异常并记录日志
            log.error("❌ ReActAgent 步骤执行失败", e);
            return "❌ 步骤执行失败：" + e.getMessage();
        }
    }
}
