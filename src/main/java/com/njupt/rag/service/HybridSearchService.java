package com.njupt.rag.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 混合检索服务。
 * <p>
 * 结合向量检索和全文检索，使用 RRF（Reciprocal Rank Fusion）算法合并结果。
 * - 向量检索：基于语义相似度
 * - 全文检索：基于关键词匹配（PostgreSQL tsvector）
 * - RRF 算法：通过倒数排名融合两路结果
 * <p>
 * 新增功能：
 * - 支持文档过滤（通过 metadata.filename 字段）
 * - 可指定检索范围，提高多文档场景下的检索精度
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HybridSearchService {

    private final VectorStore vectorStore;
    private final JdbcTemplate jdbcTemplate;

    private static final int VECTOR_SEARCH_TOP_K = 10;
    private static final int FULLTEXT_SEARCH_TOP_K = 10;
    private static final int FINAL_TOP_K = 3;
    private static final double RRF_K = 60.0; // RRF 平常数，通常取 60

    /**
     * 执行混合检索。
     * 支持文档过滤，通过 targetDocument 参数指定检索范围。
     *
     * @param query           查询文本
     * @param targetDocument   目标文档文件名（可选，如"2023级计算机学院培养方案.pdf"）
     * @return 合并后的 Top-K 文档列表
     */
    public List<Document> hybridSearch(String query, String targetDocument) {
        log.debug("开始混合检索，查询: '{}', targetDocument: '{}'", query, targetDocument);

        // 1. 向量检索（带文档过滤）
        List<Document> vectorResults = vectorSimilaritySearch(query, targetDocument);

        // 2. 全文检索（带文档过滤）
        List<Document> fullTextResults = fullTextSearch(query, targetDocument);

        // 3. 使用 RRF 合并结果
        List<Document> mergedResults = reciprocalRankFusion(vectorResults, fullTextResults);

        // 4. 返回 Top-K
        List<Document> finalResults = mergedResults.stream()
                .limit(FINAL_TOP_K)
                .collect(Collectors.toList());

        log.debug("混合检索完成，返回 {} 个结果（向量检索: {}, 全文检索: {}）",
                finalResults.size(), vectorResults.size(), fullTextResults.size());

        return finalResults;
    }

    /**
     * 向量相似度检索。
     * 支持文档过滤，通过 filterExpression 实现。
     *
     * @param query           查询文本
     * @param targetDocument   目标文档文件名（可选）
     * @return 检索结果列表
     */
    private List<Document> vectorSimilaritySearch(String query, String targetDocument) {
        SearchRequest searchRequest = SearchRequest.query(query)
                .withTopK(VECTOR_SEARCH_TOP_K);

        // 如果有文档过滤，添加过滤条件
        if (targetDocument != null && !targetDocument.isEmpty()) {
            // 使用 metadata.filename 进行过滤
            // 注意：这里使用的是 Spring AI 的 filter expression 语法，不是 SQL
            // Spring AI 会将其转换为安全的参数化查询
            String filterExpression = String.format("metadata['filename'] == '%s'", targetDocument.replace("'", "''"));
            searchRequest = searchRequest.withFilterExpression(filterExpression);
            log.debug("向量检索添加过滤条件: {}", filterExpression);
        }

        return vectorStore.similaritySearch(searchRequest);
    }

    /**
     * 全文检索（使用 PostgreSQL tsvector）。
     * 支持文档过滤，通过 SQL WHERE 条件实现。
     *
     * @param query           查询文本
     * @param targetDocument   目标文档文件名（可选）
     * @return 检索结果列表
     */
    private List<Document> fullTextSearch(String query, String targetDocument) {
        try {
            String sql;

            // 根据是否有文档过滤，选择不同的 SQL
            if (targetDocument != null && !targetDocument.isEmpty()) {
                // 有文档过滤：添加 WHERE 条件
                sql = """
                        SELECT id, content, metadata
                        FROM vector_store
                        WHERE metadata->>'filename' = ?
                          AND tsvector_content @@ plainto_tsquery('simple', ?)
                        ORDER BY ts_rank(tsvector_content, plainto_tsquery('simple', ?)) DESC
                        LIMIT ?
                        """;
                log.debug("全文检索使用文档过滤: {}", targetDocument);
            } else {
                // 没有文档过滤：全部检索
                sql = """
                        SELECT id, content, metadata
                        FROM vector_store
                        WHERE tsvector_content @@ plainto_tsquery('simple', ?)
                        ORDER BY ts_rank(tsvector_content, plainto_tsquery('simple', ?)) DESC
                        LIMIT ?
                        """;
            }

            return jdbcTemplate.query(sql, rs -> {
                List<Document> documents = new ArrayList<>();
                ObjectMapper objectMapper = new ObjectMapper();

                while (rs.next()) {
                    String id = rs.getString("id");
                    String content = rs.getString("content");
                    String metadataJson = rs.getString("metadata");

                    Map<String, Object> metadata = new HashMap<>();
                    if (metadataJson != null && !metadataJson.isEmpty()) {
                        try {
                            // 使用 Jackson 完整解析 JSON 元数据
                            Map<String, Object> parsedMetadata = objectMapper.readValue(
                                    metadataJson,
                                    new TypeReference<Map<String, Object>>() {}
                            );
                            metadata.putAll(parsedMetadata);
                        } catch (Exception e) {
                            log.warn("解析元数据失败，使用原始JSON: {}", e.getMessage());
                            metadata.put("raw_metadata", metadataJson);
                        }
                    }
                    metadata.put("id", id);

                    documents.add(new Document(content, metadata));
                }
                return documents;
            }, buildQueryParameters(sql, targetDocument, query));

        } catch (Exception e) {
            log.error("全文检索失败，返回空结果。错误: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * 构建 SQL 查询参数。
     * 根据是否有文档过滤，返回不同的参数顺序。
     *
     * @param sql            SQL 语句
     * @param targetDocument  目标文档文件名
     * @param query          查询文本
     * @return 查询参数数组
     */
    private Object[] buildQueryParameters(String sql, String targetDocument, String query) {
        if (targetDocument != null && !targetDocument.isEmpty() && sql.contains("metadata->>'filename'")) {
            // 有文档过滤：参数顺序为 filename, query, query, limit
            return new Object[]{targetDocument, query, query, FULLTEXT_SEARCH_TOP_K};
        } else {
            // 没有文档过滤：参数顺序为 query, query, limit
            return new Object[]{query, query, FULLTEXT_SEARCH_TOP_K};
        }
    }

    /**
     * RRF（Reciprocal Rank Fusion）算法合并两路检索结果。
     * <p>
     * 公式：score = sum(1 / (k + rank))，其中 k 是平衡常数，rank 是文档在各列表中的排名
     *
     * @param list1 第一路结果列表
     * @param list2 第二路结果列表
     * @return 按融合分数排序后的文档列表
     */
    private List<Document> reciprocalRankFusion(List<Document> list1, List<Document> list2) {
        // 使用文档内容作为 key（简化处理，实际应用中可用唯一 ID）
        Map<String, Double> scores = new HashMap<>();
        Map<String, Document> documentMap = new HashMap<>();

        // 处理第一路结果
        for (int i = 0; i < list1.size(); i++) {
            Document doc = list1.get(i);
            String key = getDocumentKey(doc);
            scores.put(key, scores.getOrDefault(key, 0.0) + 1.0 / (RRF_K + i + 1));
            documentMap.put(key, doc);
        }

        // 处理第二路结果
        for (int i = 0; i < list2.size(); i++) {
            Document doc = list2.get(i);
            String key = getDocumentKey(doc);
            scores.put(key, scores.getOrDefault(key, 0.0) + 1.0 / (RRF_K + i + 1));
            documentMap.put(key, doc);
        }

        // 按分数排序
        return scores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .map(entry -> documentMap.get(entry.getKey()))
                .collect(Collectors.toList());
    }

    /**
     * 获取文档的唯一标识 key。
     * 优先使用 metadata 中的 id，否则使用 content 的 hash
     */
    private String getDocumentKey(Document doc) {
        Object id = doc.getMetadata().get("id");
        if (id != null) {
            return String.valueOf(id);
        }
        // 使用 content 作为 fallback key
        return String.valueOf(doc.getContent().hashCode());
    }
}
