package com.sz.aiagent.tools;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

/**
 * 终止工具
 * 作用是让自主规划的智能体能够合理中断对话或任务流程。
 * 当所有任务完成或无法继续时，调用该工具通知终止。
 * @author zyh
 * @version 1.0.0
 * @date 2025/07/13
 */
@Component
@Slf4j
public class TerminateTool {

    /**
     * 终止交互/任务
     * 当请求满足，或助手无法继续执行任务时调用。
     * 告知智能体任务结束。
     * @return 任务结束提示文本
     * @author zyh
     * @date 2025/07/13
     */
    @Tool(description = """
            当满足结束条件时调用此工具，终止当前交互或任务。
            “当所有任务完成后，调用此工具结束工作。”
            """)
    public String doTerminate() {
        log.info("任务结束");
        return "任务结束";
    }
}
