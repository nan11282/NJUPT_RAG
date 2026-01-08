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
import org.springframework.ai.vectorstore.PgVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Spring AI 相关配置类。
 * <p>
 * 负责配置和初始化项目中使用的各类AI模型和向量数据库。
 */
@Configuration
public class AiConfig {

    /**
     * 配置聊天模型 (ChatModel)。
     * 使用 DeepSeek 的 API 作为聊天服务。
     *
     * @param properties DeepSeek 服务的配置属性
     * @return OpenAiChatModel 实例
     */
    @Bean
    public ChatModel chatModel(DeepSeekProperties properties) {
        var openAiApi = new OpenAiApi(properties.getBaseUrl(), properties.getApiKey());
        var chatOptions = OpenAiChatOptions.builder()
                .withModel(properties.getChatModel())
                .build();
        return new OpenAiChatModel(openAiApi, chatOptions);
    }

    /**
     * 基于配置好的 ChatModel 创建一个便捷的 ChatClient。
     *
     * @param chatModel 聊天模型
     * @return ChatClient 实例
     */
    @Bean
    public ChatClient chatClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel).build();
    }

    /**
     * 配置文本嵌入模型 (EmbeddingModel)。
     * 使用 SiliconFlow 的 API 作为文本嵌入服务。
     * 设置为 @Primary，作为默认的 EmbeddingModel Bean。
     *
     * @param properties SiliconFlow 服务的配置属性
     * @return OpenAiEmbeddingModel 实例
     */
    @Bean
    @Primary
    public EmbeddingModel embeddingModel(SiliconFlowProperties properties) {
        var openAiApi = new OpenAiApi(properties.getBaseUrl(), properties.getApiKey());
        var embeddingOptions = OpenAiEmbeddingOptions.builder()
                .withModel(properties.getEmbeddingModel())
                .build();
        return new OpenAiEmbeddingModel(openAiApi, MetadataMode.EMBED, embeddingOptions);
    }

    /**
     * 配置向量数据库 (VectorStore)。
     * 使用 PostgreSQL 的 pgvector 扩展进行向量存储和检索。
     *
     * @param jdbcTemplate   用于数据库操作的 Spring JDBC 模板
     * @param embeddingModel 用于生成文档向量的嵌入模型
     * @return PgVectorStore 实例
     */
    @Bean
    public VectorStore vectorStore(JdbcTemplate jdbcTemplate, EmbeddingModel embeddingModel) {
        return new PgVectorStore(
                jdbcTemplate,
                embeddingModel,
                1024, // 向量维度，与 embeddingModel 保持一致
                PgVectorStore.PgDistanceType.COSINE_DISTANCE, // 使用余弦距离进行相似度计算
                false, // 在生产环境中，不应在每次启动时删除并重建表
                PgVectorStore.PgIndexType.HNSW, // 使用 HNSW 索引以优化检索性能
                true // 自动初始化数据库 schema (如果表不存在则创建)
        );
    }

    /**
     * DeepSeek API 的配置属性类。
     * 从 application.yml 文件中读取 `spring.ai.deepseek` 前缀的配置。
     */
    @Setter
    @Getter
    @Component
    @ConfigurationProperties(prefix = "spring.ai.deepseek")
    public static class DeepSeekProperties {
        private String baseUrl;
        private String apiKey;
        private String chatModel;
    }

    /**
     * SiliconFlow API 的配置属性类。
     * 从 application.yml 文件中读取 `spring.ai.siliconflow` 前缀的配置。
     */
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
