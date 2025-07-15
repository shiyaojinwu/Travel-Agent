package com.sz.aiagent.rag.retrieval;

import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 上下文查询增强器配置类（由 Spring 管理）
 * 用于构建处理空上下文的提示逻辑。
 * @author zyh
 * @version 1.0.0
 * @date 2025/07/13
 */
@Configuration
public class TravelAppContextualQueryAugmenterConfig {

    /**
     * 注册默认的上下文查询增强器
     * 用于提示只能回答出行游玩相关的问题
     * @return {@code ContextualQueryAugmenter }
     * @author zyh
     * @date 2025/07/13
     */
    @Bean
    public ContextualQueryAugmenter travelAppContextualQueryAugmenter() {
        PromptTemplate emptyContextPromptTemplate = new PromptTemplate("""
                你应该输出下面的内容：
                抱歉，我只能回答出行游玩的相关问题，别的没办法帮到您哦
                """);

        return ContextualQueryAugmenter.builder()
                .allowEmptyContext(true)// 此处保留增强器但允许空上下文,不想为空就要改为false
                .emptyContextPromptTemplate(emptyContextPromptTemplate)
                .build();
    }
}
