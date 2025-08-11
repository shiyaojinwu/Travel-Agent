package com.sz.aiagent.rag.etl.load;

import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class KnowledgeImportConfig {
  @Bean
  @ConditionalOnProperty(name = "travel.rag.import-on-startup", havingValue = "true")
  ApplicationRunner importKnowledge(KnowledgeImporter importer) {
    return args -> importer.importKnowledge();
  }
}
