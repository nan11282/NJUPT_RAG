package com.njupt.rag.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.vectorstore.PgVectorStore; // 👈 新增
import org.springframework.ai.vectorstore.VectorStore; // 👈 新增
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate; // 👈 新增
import org.springframework.stereotype.Component; // 👈 新增

@Configuration
public class AiConfig {

    // 1. Chat 配置 (DeepSeek) - 保持不变
    @Bean
    public ChatModel chatModel(DeepSeekProperties properties) {
        var openAiApi = new OpenAiApi(properties.getBaseUrl(), properties.getApiKey());
        var chatOptions = OpenAiChatOptions.builder()
                .withModel(properties.getChatModel())
                .build();
        return new OpenAiChatModel(openAiApi, chatOptions);
    }

    @Bean
    public ChatClient chatClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel).build();
    }

    // 2. Embedding 配置 (SiliconFlow) - 保持不变
    @Bean
    @Primary
    public EmbeddingModel embeddingModel(SiliconFlowProperties properties) {
        System.out.println("========== 硅基流动配置检查 ==========");
        System.out.println("Base URL: " + properties.getBaseUrl());
        System.out.println("最终使用的模型名: " + properties.getEmbeddingModel());
        System.out.println("======================================");

        var openAiApi = new OpenAiApi(properties.getBaseUrl(), properties.getApiKey());

        // 强制修正模型名逻辑（为了保险，这里再加一次判断）
        String modelName = properties.getEmbeddingModel();
        if (modelName == null || modelName.isEmpty())
            modelName = "BAAI/bge-m3";

        var embeddingOptions = OpenAiEmbeddingOptions.builder()
                .withModel(modelName)
                .build();

        return new OpenAiEmbeddingModel(openAiApi, MetadataMode.EMBED, embeddingOptions);
    }

    // 👇👇👇 3. 新增：手动接管 VectorStore (关键！) 👇👇👇
    @Bean
    public VectorStore vectorStore(JdbcTemplate jdbcTemplate, EmbeddingModel embeddingModel) {
        // 根据你的截图提示，必须凑齐这 7 个参数
        return new PgVectorStore(
                jdbcTemplate,
                embeddingModel,
                1024, // 3. 维度
                PgVectorStore.PgDistanceType.COSINE_DISTANCE, // 4. 距离度量 (需要引入这个枚举)
                false, // 5. 是否启动时删表 (生产环境填false)
                PgVectorStore.PgIndexType.HNSW, // 6. 索引类型
                true // 7. 是否初始化Schema (自动建表)
        );
    }

    // 属性类
    @Setter
    @Getter
    @Component
    @ConfigurationProperties(prefix = "spring.ai.deepseek")
    public static class DeepSeekProperties {
        private String baseUrl;
        private String apiKey;
        private String chatModel;
    }

    @Setter
    @Getter
    @Component
    @ConfigurationProperties(prefix = "spring.ai.siliconflow")
    public static class SiliconFlowProperties {
        private String baseUrl = "https://api.siliconflow.cn/v1";
        private String apiKey;
        private String embeddingModel = "BAAI/bge-m3";
    }
}