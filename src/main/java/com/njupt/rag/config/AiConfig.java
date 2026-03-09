package com.njupt.rag.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
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
import org.springframework.util.StringUtils;
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
     * @param properties     VectorStore 配置属性
     * @return PgVectorStore 实例
     */
    @Bean
    public VectorStore vectorStore(
            JdbcTemplate jdbcTemplate,
            EmbeddingModel embeddingModel,
            VectorStoreProperties properties) {
        return new PgVectorStore(
                jdbcTemplate,
                embeddingModel,
                properties.getDimensions(), // 从 YAML 配置读取向量维度
                properties.getDistanceTypeEnum(), // 从 YAML 配置读取距离类型
                properties.isRemoveExistingVectorStoreTable(), // 从 YAML 配置读取是否重建表
                properties.getIndexTypeEnum(), // 从 YAML 配置读取索引类型
                properties.isInitializeSchema() // 从 YAML 配置读取是否初始化 schema
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

    /**
     * VectorStore 配置属性类。
     * 从 application.yml 文件中读取 `spring.ai.vectorstore.pgvector` 前缀的配置。
     * 用于控制向量数据库的连接、维度、索引等参数。
     * @ConfigurationProperties 会自动将 YAML 中的配置绑定到这些字段。
     */
    @Setter
    @Getter
    @Component
    @ConfigurationProperties(prefix = "spring.ai.vectorstore.pgvector")
    public static class VectorStoreProperties {
        /**
         * 向量维度，必须与嵌入模型的维度一致
         */
        private int dimensions;

        /**
         * 是否自动初始化数据库 schema（如果表不存在则创建）
         */
        private boolean initializeSchema;

        /**
         * 是否在启动时删除并重建表（生产环境应设置为 false）
         */
        private boolean removeExistingVectorStoreTable;

        /**
         * 索引类型：IVFFLAT（简单但性能一般）或 HNSW（高性能但内存占用大）
         */
        private String indexType;

        /**
         * 距离计算类型：COSINE_DISTANCE（余弦距离）或 EUCLIDEAN_DISTANCE（欧氏距离）
         */
        private String distanceType;

        /**
         * 获取索引类型枚举值
         */
        public PgVectorStore.PgIndexType getIndexTypeEnum() {
            if (!StringUtils.hasText(indexType)) {
                return PgVectorStore.PgIndexType.HNSW;
            }
            String normalized = indexType.toUpperCase();
            for (PgVectorStore.PgIndexType type : PgVectorStore.PgIndexType.values()) {
                if (type.name().equals(normalized)) {
                    return type;
                }
            }
            return PgVectorStore.PgIndexType.HNSW;
        }

        /**
         * 获取距离类型枚举值，处理大小写和格式差异
         *
         * @return PgDistanceType 枚举
         */
        public PgVectorStore.PgDistanceType getDistanceTypeEnum() {
            if (!StringUtils.hasText(distanceType)) {
                return PgVectorStore.PgDistanceType.COSINE_DISTANCE;
            }

            String normalized = distanceType.toUpperCase().replace("-", "_");
            for (PgVectorStore.PgDistanceType type : PgVectorStore.PgDistanceType.values()) {
                if (type.name().equals(normalized)) {
                    return type;
                }
            }

            return PgVectorStore.PgDistanceType.COSINE_DISTANCE;
        }
    }
}
