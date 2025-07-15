package com.sz.aiagent.tools.registration;

import com.sz.aiagent.tools.*;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbacks;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ✅ 集中式工具注册配置类
 * 统一管理所有 AI 交互使用的工具类（Tool）实例，
 * 通过 Spring 容器注入各工具 Bean 并组装为 ToolCallback 数组供框架调用。
 * @author zyh
 * @version 1.0.0
 * @date 2025/07/13
 */
@Configuration
public class ToolRegistration {

    private final FileOperationTool fileOperationTool;

    private final WebSearchTool webSearchTool;

    private final WebScrapingTool webScrapingTool;

    private final ResourceDownloadTool resourceDownloadTool;

    private final TerminalOperationTool terminalOperationTool;

    private final PDFGenerationTool pdfGenerationTool;

    private final TerminateTool terminateTool;

    /**
     * 通过构造器注入所有工具 Bean
     * @param fileOperationTool     文件读写工具
     * @param webSearchTool         网络搜索工具
     * @param webScrapingTool       网页抓取工具
     * @param resourceDownloadTool  资源下载工具
     * @param terminalOperationTool 终端命令执行工具
     * @param pdfGenerationTool     PDF 生成工具
     * @param terminateTool         终止任务工具
     * @author zyh
     * @date 2025/07/13
     */
    public ToolRegistration(FileOperationTool fileOperationTool,
                            WebSearchTool webSearchTool,
                            WebScrapingTool webScrapingTool,
                            ResourceDownloadTool resourceDownloadTool,
                            TerminalOperationTool terminalOperationTool,
                            PDFGenerationTool pdfGenerationTool,
                            TerminateTool terminateTool) {
        this.fileOperationTool = fileOperationTool;
        this.webSearchTool = webSearchTool;
        this.webScrapingTool = webScrapingTool;
        this.resourceDownloadTool = resourceDownloadTool;
        this.terminalOperationTool = terminalOperationTool;
        this.pdfGenerationTool = pdfGenerationTool;
        this.terminateTool = terminateTool;
    }

    /**
     * 将所有工具组装为 ToolCallback 数组，供 AI 框架调用注册
     * @return 所有工具的 ToolCallback 数组
     * @author zyh
     * @date 2025/07/13
     */
    @Bean
    public ToolCallback[] allTools() {
        return ToolCallbacks.from(
                fileOperationTool,
                webSearchTool,
                webScrapingTool,
                resourceDownloadTool,
                terminalOperationTool,
                pdfGenerationTool,
                terminateTool
        );
    }
}
