package com.sz.aiagent.tools;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * 网页抓取工具
 * 使用 Jsoup 抓取指定网页内容（HTML 全文）。
 * 可供 AI 在对话中调用，用于分析网页结构、提取文本、辅助信息检索等。
 * @author zyh
 * @version 1.0.0
 * @date 2025/07/13
 */
@Component
@Slf4j
public class WebScrapingTool {

    /**
     * 抓取网页内容
     * 支持传入 URL，由工具自动发起请求并返回 HTML 源码。
     * 可用于网页分析、摘要生成、数据提取等任务。
     * @param url 要抓取的网页地址（完整 URL）
     * @return 返回该网页的 HTML 全文，若失败则返回错误信息
     * @author zyh
     * @date 2025/07/13
     */
    @Tool(description = "抓取网页的完整 HTML 内容")
    public String scrapeWebPage(@ToolParam(description = "要抓取的网页链接地址") String url) {
        try {
            // 使用 Jsoup 发起连接并获取页面
            Document document = Jsoup.connect(url).get();
            // 返回 HTML 全文
            log.info("抓取网页成功，网页链接为：{}", url);
            return document.html();
        } catch (Exception e) {
            // 异常处理，防止 AI 工具调用失败
            log.error("抓取网页失败，原因：{}", e.getMessage());
            return "抓取网页失败，原因：" + e.getMessage();
        }
    }
}
