package com.njupt.rag;

import org.springframework.ai.autoconfigure.openai.OpenAiAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.ai.autoconfigure.vectorstore.pgvector.PgVectorStoreAutoConfiguration;

/**
 * Spring Boot 应用主入口。
 * <p>
 * 通过 {@code @SpringBootApplication} 注解启动整个应用。
 * <p>
 * 特别地，这里通过 {@code exclude} 参数禁用了 Spring AI 对 OpenAI 和 PgVectorStore 的自动配置。
 * 这是因为在 {@link com.njupt.rag.config.AiConfig} 中我们已经提供了自定义的、更灵活的配置 Bean，
 * 从而避免了自动配置可能带来的冲突或不确定性。
 */
@SpringBootApplication(exclude = {
        PgVectorStoreAutoConfiguration.class, // 禁用PgVectorStore的自动配置
        OpenAiAutoConfiguration.class         // 禁用OpenAI相关服务的自动配置
})
public class NjupterRagApplication {

    /**
     * 应用主方法。
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(NjupterRagApplication.class, args);
    }

}
