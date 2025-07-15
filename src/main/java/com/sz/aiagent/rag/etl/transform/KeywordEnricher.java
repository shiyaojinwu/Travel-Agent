package com.sz.aiagent.rag.etl.transform;

import jakarta.annotation.Resource;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.KeywordMetadataEnricher;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 文档元数据增强组件
 * 功能：基于 AI 模型为文档自动提取关键词，补充到 metadata 中，用于增强文档语义索引与筛选能力。
 * - 构建向量知识库前为每个文档生成关键词
 * - 供后续向量检索（如状态筛选、语义筛选）使用
 * @author zyh
 * @version 1.0.0
 * @date 2025/07/13
 */
@Component
@SuppressWarnings("unused")
public class KeywordEnricher {

    /**
     * 注入用于生成关键词的 ChatModel，一般为大语言模型（如通义千问）
     */
    @Resource
    private ChatModel dashscopeChatModel;

    /**
     * 为输入的文档集合提取关键词并写入 metadata 字段中（字段名为 "keywords"）
     * @param documents 原始文档列表
     * @return 含关键词 metadata 的增强文档列表
     * @author zyh
     * @date 2025/07/13
     */
    public List<Document> enrichDocuments(List<Document> documents) {
        // 使用 Spring AI 提供的 KeywordMetadataEnricher 工具，自动生成关键词（最多 5 个）
        KeywordMetadataEnricher keywordMetadataEnricher = new KeywordMetadataEnricher(dashscopeChatModel, 5);
        // 对文档列表应用关键词提取
        return keywordMetadataEnricher.apply(documents);
    }
}
