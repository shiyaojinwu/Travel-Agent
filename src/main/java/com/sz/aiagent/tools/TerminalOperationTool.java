package com.sz.aiagent.tools;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * ✅ 终端操作工具（Windows 环境）
 * 用于执行终端命令（如 dir、ping等），将输出结果返回给 AI 模型。
 * ⚠️ 默认使用 Windows 的 cmd.exe 执行命令，请确保命令格式正确。
 * @author zyh
 * @version 1.0.0
 * @date 2025/07/13
 */
@Component
@Slf4j
public class TerminalOperationTool {

    /**
     * 执行终端命令
     * 被大模型调用后，可执行指定的系统命令，并返回标准输出内容。
     * @param command 要执行的命令（如 "dir", "ping www.baidu.com"）
     * @return 返回命令的执行结果（标准输出），或错误信息
     * @author zyh
     * @date 2025/07/13
     */
    @Tool(description = "执行终端命令并返回输出结果")
    public String executeTerminalCommand(@ToolParam(description = "要在终端中执行的命令") String command) {
        StringBuilder output = new StringBuilder();
        try {
            // 使用 ProcessBuilder 构造 Windows 命令执行器（cmd /c <command>）
            ProcessBuilder builder = new ProcessBuilder("cmd.exe", "/c", command);
            // 启动进程
            Process process = builder.start();
            // 读取命令执行过程中的标准输出流
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }
            // 等待命令执行完成
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                output.append("命令执行失败，退出码为：").append(exitCode);
            }

        } catch (IOException | InterruptedException e) {
            // 捕获执行或线程中断异常
            output.append("执行命令出错：").append(e.getMessage());
        }
        log.info("命令执行完成，输出结果为：{}", output);
        return output.toString();
    }
}
