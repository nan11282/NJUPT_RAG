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
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 文档处理与入库服务。
。
 * 负责将外部文档（如PDF）加载、解析、切分、向量化，并最终存入向量数据库。
 * 支持两种加载方式：
 * 1. 应用启动时自动从 `classpath:data/` 目录加载。
 * 2. 通过 {@link com.njupt.rag.controller.DocumentController} API手动触发加载。
 *
 * 新增功能：
 * - 使用文件哈希检测文档内容变化
 * - 文档被修改时自动删除旧数据并重新处理
 * - 支持文档级别的过滤
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
     * 该方法会检查文档是否已存在或被修改，智能决定是否需要重新处理。
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

                // 使用文件哈希检查文档状态
                FileUpdateStatus status = checkFileUpdateStatus(resource);

                switch (status) {
                    case NEW_FILE:
                        log.info("📄 [新文件] 处理 '{}'", filename);
                        processDocument(resource);
                        break;

                    case MODIFIED:
                        log.info("🔄 [文件已修改] 删除旧数据并重新处理 '{}'", filename);
                        deleteDocumentData(filename);
                        processDocument(resource);
                        break;

                    case UNCHANGED:
                        log.info("⏩ [未变化] 跳过 '{}'", filename);
                        break;

                    case SKIP:
                        log.warn("⚠️ [跳过] '{}' 状态未知，跳过。", filename);
                        break;
                }
            }
            log.info("--- 文档自动加载服务检查完毕 ---");

        } catch (IOException e) {
            log.error("启动时扫描文档失败。", e);
        }
    }

    /**
     * 文件更新状态枚举。
     */
    private enum FileUpdateStatus {
        NEW_FILE,      // 新文件，需要处理
        MODIFIED,       // 文件被修改，需要删除旧数据后重新处理
        UNCHANGED,      // 文件没变，跳过
        SKIP           // 出错了，跳过
    }

    /**
     * 检查文件是否需要更新。
     * <p>
     * 通过计算文件内容的 SHA-256 哈希值并与数据库中存储的哈希值比较来判断。
     * 如果文件不存在于数据库 → 需要处理
     * 如果文件存在但哈希不同 → 需要更新（删除旧数据，重新处理）
     * 如果文件存在且哈希相同 → 跳过
     *
     * @param resource 文件资源
     * @return 文件更新状态
     */
    private FileUpdateStatus checkFileUpdateStatus(Resource resource) {
        String filename = resource.getFilename();
        if (filename == null) {
            return FileUpdateStatus.SKIP;
        }

        // 计算当前文件的哈希值
        String currentHash = calculateFileHash(resource);
        if (currentHash == null) {
            return FileUpdateStatus.SKIP;
        }

        try {
            // 查询数据库中的哈希值（只取 1 行即可）
            String sql = """
                    SELECT metadata->>'file_hash' as hash
                    FROM vector_store
                    WHERE metadata->>'filename' = ?
                    LIMIT 1
                    """;

            String dbHash = jdbcTemplate.queryForObject(sql, String.class, filename);

            if (dbHash == null) {
                // // 数据库里没有这个文件 → 新文件
                return FileUpdateStatus.NEW_FILE;
            }

            if (!dbHash.equals(currentHash)) {
                // // 哈希不同 → 文件被修改了
                return FileUpdateStatus.MODIFIED;
            }

            // // 哈希相同 → 文件没变
            return FileUpdateStatus.UNCHANGED;

        } catch (EmptyResultDataAccessException e) {
            // // 没查到记录 → 新文件
            return FileUpdateStatus.NEW_FILE;
        } catch (Exception e) {
            log.error("检查文件更新状态失败: {}", e.getMessage(), e);
            return FileUpdateStatus.SKIP;
        }
    }

    /**
     * 计算资源的哈希值（用于检测文件内容是否变化）。
     * <p>
     * 使用 SHA-256 算法计算文件内容的哈希值。
     *
     * @param resource 文件资源
     * @return SHA-256 哈希值（64 位十六进制字符串），计算失败返回 null
     */
    private String calculateFileHash(Resource resource) {
        try (InputStream is = resource.getInputStream()) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int read;

            while ((read = is.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }

            byte[] hashBytes = digest.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }

            return sb.toString();

        } catch (Exception e) {
            log.error("计算文件哈希失败: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 删除指定文件的所有数据。
     * <p>
     * 根据文件名删除所有对应的文本块。
     *
     * @param filename 文件名
     */
    public void deleteDocumentData(String filename) {
        if (filename == null) {
            return;
        }

        try {
            String sql = "DELETE FROM vector_store WHERE metadata->>'filename' = ?";
            int deleted = = jdbcTemplate.update(sql, filename);
            log.info("已删除文件 '{}' 的 {} 条旧数据。", filename, deleted);
        } catch (Exception e) {
            log.error("删除文件数据失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 对单个文档资源执行完整的处理流程。
     * <p>
     * 流程包括：计算哈希 → 读取内容 → 文本切块 → 添加元数据 → 向量化并存储。
     *
     * @param resource 要处理的文档资源
     */
    public void processDocument(Resource resource) {
        String filename = resource.getFilename();
        if (filename == null) {
            log.warn("正在处理一个没有文件名的资源。");
            return;
        }

        // 计算文件哈希
        String fileHash = calculateFileHash(resource);
        if (fileHash == null) {
            log.error("无法计算文件哈希，跳过处理 '{}'", filename);
            return;
        }

        try {
            log.info("--- 开始处理文档: {} ---", filename);

            log.info("[1/5] 计算文件哈希: {}...", fileHash.substring(0, 16) + "...");

            log.info("[2/5] 使用 Tika 读取文档内容...");
            TikaDocumentReader reader = new TikaDocumentReader(resource);
            List<Document> documents = reader.get();

            log.info("[3/5] 使用中文递归分割器进行文本切块...");
            ChineseRecursiveTextSplitter splitter = new ChineseRecursiveTextSplitter(500, 50);
            List<Document> splitDocs = splitter.apply(documents);
            log.info("      切分完成，生成 {} 个文本块。", splitDocs.size());

            log.info("[4/5] 为每个文本块添加元数据 (filename, upload_time, file_hash)... ");
            long now = Instant.now().toEpochMilli();
            splitDocs.forEach(doc -> {
                doc.getMetadata().put("filename", filename);
                doc.getMetadata().put("upload_time", String.valueOf(now));
                doc.getMetadata().put("file_hash", fileHash);  // 保存文件哈希
            });

            log.info("[5/5] 将文本块提交至 VectorStore 进行向量化和存储...");
            vectorStore.add(splitDocs);
            log.info("--- ✅ 成功入库来自 '{}' 的 {} 个文本块。 ---", filename, splitDocs.size());

        } catch (Exception e) {
            log.error("--- ❌ 处理文档 '{}' 时发生严重错误。 ---", filename, e);
            throw new RuntimeException("处理文档失败: " + filename, e);
        }
    }
}
