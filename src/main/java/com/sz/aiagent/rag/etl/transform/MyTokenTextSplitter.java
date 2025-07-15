package com.sz.aiagent.rag.etl.transform;

import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 自定义基于 Token 的文本切分器
 * 该组件用于将文本文档按 Token 数量进行切分，
 * 方便后续进行向量化或索引构建，适配长文本处理。
 * @author zyh
 * @version 1.0.0
 * @date 2025/07/13
 */
@Component
@SuppressWarnings("unused")
public class MyTokenTextSplitter {

    /**
     * 默认切分方法
     * 使用 TokenTextSplitter 默认配置对文档列表进行切分
     * @param documents 原始文档列表
     * @return 切分后的文档列表
     * @author zyh
     * @date 2025/07/13
     */
    public List<Document> splitDocuments(List<Document> documents) {
        TokenTextSplitter splitter = new TokenTextSplitter();
        return splitter.apply(documents);
    }

    /**
     * 自定义切分方法
     * 使用自定义参数创建 TokenTextSplitter，控制切分粒度
     * @param documents 原始文档列表
     * @return 切分后的文档列表
     * @author zyh
     * @date 2025/07/13
     */
    public List<Document> splitCustomized(List<Document> documents) {
        // 参数说明：
        // chunkSize = 200 ：每个切片最大Token数
        // minChunkSizeChars = 100 ：切片重叠的最小Token数
        // minChunkLengthToEmbed = 10 ：切片大小需是此数的倍数
        // maxNumChunks = 5000 ：最大切片大小限制
        // keepSeparator = true ：是否允许截断
        TokenTextSplitter splitter = new TokenTextSplitter(200, 100, 10, 5000, true);
        return splitter.apply(documents);
    }
}
