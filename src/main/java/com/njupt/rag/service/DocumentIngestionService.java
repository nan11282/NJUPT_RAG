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
 * 负责文档的加载、解析、分割和入库。
 * 支持应用启动时自动加载和通过API手动加载。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentIngestionService implements ResourceLoaderAware {
    @Autowired
    private final VectorStore vectorStore;

    private ResourceLoader resourceLoader;

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void setResourceLoader(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    /**
     * 应用启动后，自动从 classpath:data/ 目录加载 PDF 文档。
     */

    @PostConstruct
    public void loadDocumentsOnStartup() {
        Assert.notNull(resourceLoader, "ResourceLoader has not been initialized.");
        try {
            log.info("--- Document Ingestion Service: Starting automatic document loading ---");
            ResourcePatternResolver resolver = (ResourcePatternResolver) resourceLoader;
            Resource[] resources = resolver.getResources("classpath*:data/*.pdf");

            if (resources.length == 0) {
                log.warn("No PDF files found in 'classpath:data/'.");
                return;
            }

            log.info("Scanning {} documents in 'classpath:data/'...", resources.length);

            for (Resource resource : resources) {
                String filename = resource.getFilename();

                // 👇👇👇 核心优化：先检查是否存在 👇👇👇
                if (isDocumentAlreadyIngested(filename)) {
                    log.info("⏩ [SKIP] Document already exists in DB: {}", filename);
                    continue; // 跳过本次循环，不读取，不切分，不Embedding
                }

                // 如果不存在，才进行处理
                processDocument(resource);
            }
            log.info("--- Document Ingestion Service: Startup check complete ---");

        } catch (IOException e) {
            log.error("Failed to scan for documents on startup.", e);
        }
    }

    /**
     * 检查文档是否已经存在于向量库中 (基于 metadata->>'filename')
     */
    private boolean isDocumentAlreadyIngested(String filename) {
        if (filename == null) return false;
        try {
            // 直接查 PostgreSQL 的 vector_store 表，看 metadata 字段里有没有这个文件名
            // 这种方式最快，不消耗 Token
            String sql = "SELECT COUNT(1) FROM vector_store WHERE metadata->>'filename' = ?";
            Integer count = jdbcTemplate.queryForObject(sql, Integer.class, filename);
            return count != null && count > 0;
        } catch (Exception e) {
            // 如果表还没建，或者报错，为了安全起见返回 false (尝试重新入库)
            log.warn("Could not check document existence (table might not exist yet): {}", e.getMessage());
            return false;
        }
    }
    /**
     * 处理单个文档资源：读取、分割、添加元数据并存入向量库。
     *
     * @param resource The document resource (e.g., an uploaded file).
     */
    public void processDocument(Resource resource) {
        String filename = resource.getFilename();
        if (filename == null) {
            log.warn("Processing a resource with no filename.");
            return;
        }

        try {
            log.info("==================================================");
            log.info("[1/5] Reading document content from: {}", filename);
            TikaDocumentReader reader = new TikaDocumentReader(resource);
            List<Document> documents = reader.get();
            log.info("[2/5] Document read successfully. Found {} base documents.", documents.size());

            log.info("[3/5] Splitting documents into chunks...");
            ChineseRecursiveTextSplitter splitter = new ChineseRecursiveTextSplitter(500, 50);
            List<Document> splitDocs = splitter.apply(documents);
            log.info("[3/5] Splitting complete. Generated {} text chunks.", splitDocs.size());

            log.info("[4/5] Adding metadata (filename, upload_time) to each chunk...");
            long now = Instant.now().toEpochMilli();
            splitDocs.forEach(doc -> {
                doc.getMetadata().put("filename", filename);
                doc.getMetadata().put("upload_time", String.valueOf(now));
            });
            log.info("[4/5] Metadata added successfully.");

            log.info("[5/5] Calling VectorStore to add documents. This will trigger the Embedding API call...");
            vectorStore.add(splitDocs);
            log.info("--- SUCCESS: Successfully ingested {} chunks from document: {} ---", splitDocs.size(), filename);

        } catch (Exception e) {
            log.error("--- FAILURE: An error occurred while processing document: {} ---", filename);
            log.error("Root Cause: {}. Check the stack trace below for details.", e.getMessage());
            throw new RuntimeException("Failed to process document: " + filename, e);
        }
    }
}
