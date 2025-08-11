package com.sz.aiagent.rag.etl.load;

import static org.springframework.ai.vectorstore.pgvector.PgVectorStore.PgDistanceType.COSINE_DISTANCE;
import static org.springframework.ai.vectorstore.pgvector.PgVectorStore.PgIndexType.HNSW;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class PgVectorVectorStoreConfig {
  @Bean("pgVectorVectorStore")
  public VectorStore vectorStore(
      JdbcTemplate jdbc,
      EmbeddingModel model,
      @Value("${travel.rag.dimensions:1536}") int dimensions) {
    return PgVectorStore.builder(jdbc, model)
        .dimensions(dimensions)
        .distanceType(COSINE_DISTANCE)
        .indexType(HNSW)
        .initializeSchema(true)
        .schemaName("public")
        .vectorTableName("vector_store")
        .maxDocumentBatchSize(20)
        .build();
  }
}
