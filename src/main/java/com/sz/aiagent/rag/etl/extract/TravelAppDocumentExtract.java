package com.sz.aiagent.rag.etl.extract;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.markdown.MarkdownDocumentReader;
import org.springframework.ai.reader.markdown.config.MarkdownDocumentReaderConfig;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 应用文档抽取器，载入内存并按 Markdown 的结构语义做了“逻辑分段”；
 * 该组件用于批量加载 classpath 下的 markdown 文档，
 * 并将其转换为 Spring AI 框架识别的 {@link Document} 对象，
 * 供后续向量化存储、RAG 检索增强使用。
 * - 文档按横线 (---) 分割为多个 Document
 * - 过滤代码块和引用块，避免干扰 embedding
 * - 自动添加文件名作为元数据字段 filename
 * @author zyh
 * @version 1.0.0
 * @date 2025/07/13
 */
@Component
@Slf4j
public class TravelAppDocumentExtract {

    /**
     * Spring 提供的资源解析器，用于通过通配符路径加载资源文件
     */
    private final ResourcePatternResolver resourcePatternResolver;

    /**
     * 构造方法（由 Spring 自动注入 ResourcePatternResolver 实现）
     * @param resourcePatternResolver Spring 提供的路径匹配资源解析器
     * @author zyh
     * @date 2025/07/13
     */
    public TravelAppDocumentExtract(ResourcePatternResolver resourcePatternResolver) {
        this.resourcePatternResolver = resourcePatternResolver;
    }

    /**
     * 加载 classpath:document/*.md 下所有 markdown 文件，
     * 并解析为 Document 列表。
     * @return 解析后的 Document 对象集合
     * @author zyh
     * @date 2025/07/13
     */
    public List<Document> loadMarkdowns() {
        List<Document> allDocuments = new ArrayList<>();
        try {
            // 1️⃣ 获取所有 document 目录下的 .md 文件资源
            Resource[] resources = resourcePatternResolver.getResources("classpath:document/*.md");
            // 2️⃣ 遍历每个资源文件
            for (Resource resource : resources) {
                String filename = resource.getFilename();
                // 3️⃣ 配置 Markdown 解析行为
                MarkdownDocumentReaderConfig config = MarkdownDocumentReaderConfig.builder()
                        .withHorizontalRuleCreateDocument(true)           // 每个水平分隔线（---）拆分为新文档
                        .withIncludeCodeBlock(false)                      // 不包含代码块（```中的内容）
                        .withIncludeBlockquote(false)                     // 不包含引用块（> 引导的段落）
                        .withAdditionalMetadata("filename", filename)    // 加入文件名作为元数据字段
                        .build();
                // 4️⃣ 创建解析器并读取内容为多个 Document 对象
                MarkdownDocumentReader markdownDocumentReader = new MarkdownDocumentReader(resource, config);
                allDocuments.addAll(markdownDocumentReader.get());
            }
        } catch (IOException e) {
            log.error("Markdown 文档加载失败", e);
        }
        return allDocuments;
    }
}
