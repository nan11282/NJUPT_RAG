package com.njupt.rag.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

/**
 * 查询改写服务。
 * <p>
 * 将用户的口语化问题改写为适合文档检索的标准表达，提升检索准确性。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QueryRewriteService {

    private final ChatClient chatClient;

    private static final String REWRITE_PROMPT = """
            将用户的口语化问题改写为适合文档检索的标准表达。
            要求：
            1. 保留问题的核心意图和关键信息
            2. 使用更正式、规范的表述
            3. 只返回改写后的问题，不要解释
            4. 不要添加任何额外说明或前缀
            """;

    /**
     * 改写用户查询。
     *
     * @param originalQuery 原始查询
     * @return 改写后的查询，如果改写失败则返回原始查询
     */
    public String rewriteQuery(String originalQuery) {
        try {
            log.debug("开始改写查询: '{}'", originalQuery);

            String rewrittenQuery = chatClient.prompt()
                    .system(REWRITE_PROMPT)
                    .user(originalQuery)
                    .call()
                    .content()
                    .trim();

            if (rewrittenQuery.isEmpty()) {
                log.warn("改写后的查询为空，使用原始查询");
                return originalQuery;
            }

            log.debug("查询改写完成: '{}' -> '{}'", originalQuery, rewrittenQuery);
            return rewrittenQuery;

        } catch (Exception e) {
            log.error("查询改写失败，使用原始查询。错误: {}", e.getMessage(), e);
            return originalQuery;
        }
    }
}
