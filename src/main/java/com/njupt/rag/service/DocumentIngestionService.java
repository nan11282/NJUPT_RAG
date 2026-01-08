package com.njupt.rag.service;

import com.njupt.rag.utils.ChineseRecursiveTextSplitter;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

/**
 * 文档处理与入库服务。
 * <p>
 * 负责将外部文档（如PDF）加载、解析、切分、向量化，并最终存入向量数据库。
 * 支持两种加载方式：
 * 1. 应用启动时自动从 `classpath:data/` 目录加载。
 * 2. 通过 {@link com.njupt.rag.controller.DocumentController} API手动触发加载。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentIngestionService implements ResourceLoaderAware {

    private final VectorStore vectorStore;
    private final JdbcTemplate jdbcTemplate;
    private ResourceLoader resourceLoader;

    @Override
    public void setResourceLoader(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    /**
     * 在应用启动后执行，自动扫描并加载指定目录下的文档。
     * <p>
     * 使用 {@link PostConstruct} 注解确保在依赖注入完成后执行。
     * 该方法会检查文档是否已存在，避免重复入库。
     */
    @PostConstruct
    public void loadDocumentsOnStartup() {
        Assert.notNull(resourceLoader, "ResourceLoader must be initialized.");
        try {
            log.info("--- 启动文档自动加载服务 ---");
            ResourcePatternResolver resolver = (ResourcePatternResolver) resourceLoader;
            Resource[] resources = resolver.getResources("classpath*:data/*.pdf");

            if (resources.length == 0) {
                log.warn("在 'classpath:data/' 目录下未找到任何PDF文件。");
                return;
            }

            log.info("在 'classpath:data/' 目录中扫描到 {} 个文档。", resources.length);

            for (Resource resource : resources) {
                String filename = resource.getFilename();
                // 核心优化：入库前检查，确保幂等性，避免重复处理和不必要的API调用
                if (isDocumentAlreadyIngested(filename)) {
                    log.info("⏩ [跳过] 文档 '{}' 已存在于数据库中。", filename);
                    continue;
                }
                // 如果文档不存在，则执行完整的处理流程
                processDocument(resource);
            }
            log.info("--- 文档自动加载服务检查完毕 ---");

        } catch (IOException e) {
            log.error("启动时扫描文档失败。", e);
        }
    }

    /**
     * 检查指定文件名的文档是否已经被处理并存入向量数据库。
     * <p>
     * 通过直接查询数据库中存储的元数据（filename）来实现，效率高，不消耗Embedding API额度。
     *
     * @param filename 要检查的文件名
     * @return 如果文档已存在，返回 true；否则返回 false。
     */
    private boolean isDocumentAlreadyIngested(String filename) {
        if (filename == null) return false;
        try {
            String sql = "SELECT COUNT(1) FROM vector_store WHERE metadata->>'filename' = ?";
            Integer count = jdbcTemplate.queryForObject(sql, Integer.class, filename);
            return count != null && count > 0;
        } catch (Exception e) {
            // 如果表不存在或查询失败，则默认文档不存在，允许后续流程尝试入库
            log.warn("无法检查文档是否存在（可能表尚未创建）: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 对单个文档资源执行完整的处理流程。
     * <p>
     * 流程包括：读取内容 -> 文本切块 -> 添加元数据 -> 向量化并存储。
     *
     * @param resource 要处理的文档资源
     */
    public void processDocument(Resource resource) {
        String filename = resource.getFilename();
        if (filename == null) {
            log.warn("正在处理一个没有文件名的资源。");
            return;
        }

        try {
            log.info("--- 开始处理新文档: {} ---", filename);

            log.info("[1/4] 使用 Tika 读取文档内容...");
            TikaDocumentReader reader = new TikaDocumentReader(resource);
            List<Document> documents = reader.get();

            log.info("[2/4] 使用中文递归分割器进行文本切块...");
            ChineseRecursiveTextSplitter splitter = new ChineseRecursiveTextSplitter(500, 50);
            List<Document> splitDocs = splitter.apply(documents);
            log.info("      切分完成，生成 {} 个文本块。", splitDocs.size());

            log.info("[3/4] 为每个文本块添加元数据 (filename, upload_time)... ");
            long now = Instant.now().toEpochMilli();
            splitDocs.forEach(doc -> {
                doc.getMetadata().put("filename", filename);
                doc.getMetadata().put("upload_time", String.valueOf(now));
            });

            log.info("[4/4] 将文本块提交至 VectorStore 进行向量化和存储...");
            vectorStore.add(splitDocs);
            log.info("--- ✅ 成功入库来自 '{}' 的 {} 个文本块。 ---", filename, splitDocs.size());

        } catch (Exception e) {
            log.error("--- ❌ 处理文档 '{}' 时发生严重错误。 ---", filename, e);
            throw new RuntimeException("处理文档失败: " + filename, e);
        }
    }
}
