package com.sz.aiagent.rag.etl.load;

import com.sz.aiagent.rag.etl.extract.TravelAppDocumentExtract;
import jakarta.annotation.Resource;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 载入向量数据库配置类
 * 作用：初始化一个基于内存的向量数据库 Bean（SimpleVectorStore），
 * 并将 markdown 文档转为向量后写入，用于后续 RAG（检索增强生成）调用。
 */
@Configuration
public class TravelAppVectorStoreConfig {
    /**
     * 注入文档加载器：从 markdown 文件中读取知识内容并转换为 Document 列表
     */
    @Resource
    private TravelAppDocumentExtract travelAppDocumentExtract;

    /**
     * 创建并初始化 VectorStore（向量数据库）Bean
     * @param dashscopeEmbeddingModel 向量化模型（EmbeddingModel 接口），通常来自阿里云 DashScope 等 LLM 向量模型服务
     * @return VectorStore（内存向量库），可供 RAG 检索使用
     * @author zyh
     * @date 2025/07/13
     */
    @Bean("travelAppVectorStore")
    VectorStore travelAppVectorStore(EmbeddingModel dashscopeEmbeddingModel) {
        // ✅ 构建基于内存实现的 VectorStore（SimpleVectorStore）
        SimpleVectorStore simpleVectorStore = SimpleVectorStore
                .builder(dashscopeEmbeddingModel) // 指定向量模型
                .build();
        // ✅ 加载并解析 markdown 文档为 Document 列表
        List<Document> documentList = travelAppDocumentExtract.loadMarkdowns();
        // ✅ 将文档添加到内存向量库中（会自动完成 Embedding 向量转换）
        simpleVectorStore.add(documentList);
        // ✅ 返回向量存储对象，供系统注入使用
        return simpleVectorStore;
    }
}
