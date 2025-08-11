package com.sz.aiagent.rag.retrieval;

import com.sz.aiagent.rag.etl.load.KnowledgeImporter;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;
import org.springframework.ai.rag.preretrieval.query.transformation.CompressionQueryTransformer;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class TravelAppRagCustomAdvisorFactory {
  private final ContextualQueryAugmenter augmenter;
  private final ChatModel model;

  @Value("${travel.rag.top-k:3}")
  private int topK;

  @Value("${travel.rag.similarity-threshold:0.5}")
  private double threshold;

  @Value("${travel.rag.version:v1}")
  private String version;

  @Value("${travel.rag.query-rewrite:true}")
  private boolean rewrite;

  public TravelAppRagCustomAdvisorFactory(ContextualQueryAugmenter augmenter, ChatModel model) {
    this.augmenter = augmenter;
    this.model = model;
  }

  public Advisor createTravelRagAdvisor(VectorStore store) {
    var filters = new FilterExpressionBuilder();
    var retriever =
        VectorStoreDocumentRetriever.builder()
            .vectorStore(store)
            .topK(topK)
            .similarityThreshold(threshold)
            .filterExpression(
                filters
                    .and(
                        filters.eq("dataset", KnowledgeImporter.DATASET),
                        filters.eq("version", version))
                    .build())
            .build();
    var builder =
        RetrievalAugmentationAdvisor.builder()
            .documentRetriever(retriever)
            .queryAugmenter(augmenter)
            .order(100); // Memory advisor must populate history first.
    if (rewrite)
      builder.queryTransformers(
          CompressionQueryTransformer.builder()
              .chatClientBuilder(ChatClient.builder(model))
              .build());
    return builder.build();
  }
}
