package com.sz.aiagent.rag.etl.load;

import com.sz.aiagent.rag.etl.extract.TravelAppDocumentExtract;
import com.sz.aiagent.rag.etl.transform.KeywordEnricher;
import jakarta.annotation.Resource;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.springframework.ai.vectorstore.pgvector.PgVectorStore.PgDistanceType.COSINE_DISTANCE;
import static org.springframework.ai.vectorstore.pgvector.PgVectorStore.PgIndexType.HNSW;

/**
 * 向量存储配置（使用 PostgreSQL + pgvector 实现）
 * @author zyh
 * @version 1.0.0
 * @date 2025/07/13
 */
@Configuration
@SuppressWarnings("unused")
public class PgVectorVectorStoreConfig {

    /**
     * 文档抽取器
     */
    @Resource
    private TravelAppDocumentExtract travelAppDocumentExtract;

    @Resource
    private KeywordEnricher keywordEnricher;

    /**
     * 构建一个基于 PostgreSQL + pgvector 的向量存储 VectorStore
     * @param jdbcTemplate            JDBC 数据源
     * @param dashscopeEmbeddingModel 向量嵌入模型（如 DashScope）
     * @return VectorStore 实例（用于 RAG 检索）
     * @author zyh
     * @date 2025/07/13
     */
    @Bean("pgVectorVectorStore")
    public VectorStore pgVectorVectorStore(JdbcTemplate jdbcTemplate, EmbeddingModel dashscopeEmbeddingModel) {
        // 加载文档 用过一次就行了，不要重复插入
        // List<Document> documents = travelAppDocumentExtract.loadMarkdowns();
        // keywordEnricher.enrichDocuments(documents);
        // ✅ 创建 pgvector 向量存储对象
        return PgVectorStore.builder(jdbcTemplate, dashscopeEmbeddingModel)
                .distanceType(COSINE_DISTANCE)       // 相似度度量方式（默认：余弦相似度）
                .indexType(HNSW)                     // 索引类型（HNSW 适合近似最近邻搜索）
                .initializeSchema(true)              // 自动初始化 schema（建表、建索引）
                .schemaName("public")                // 指定使用的数据库 schema
                .vectorTableName("vector_store")     // 指定向量表名（默认 vector_store）
                .maxDocumentBatchSize(10000)         // 每批最大写入文档数（默认 10000）
                .build();
    }
}
