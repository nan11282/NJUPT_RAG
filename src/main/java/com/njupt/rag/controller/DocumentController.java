package com.njupt.rag.controller;

import com.njupt.rag.service.DocumentIngestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * 文档管理相关的API端点。
 * <p>
 * 专用于处理知识库文档的上传操作。
 */
@Slf4j
@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
@CrossOrigin(origins = "*") // 允许所有来源的跨域请求，方便前后端分离开发和调试
public class DocumentController {

    private final DocumentIngestionService documentIngestionService;

    /**
     * 接收并处理上传的文档文件。
     * <p>
     * 文件将被传递给 {@link DocumentIngestionService} 进行后续的解析、切分和向量化处理。
     *
     * @param file 用户通过 multipart/form-data 方式上传的文件
     * @return 包含操作结果信息（成功或失败）的 ResponseEntity
     */
    @PostMapping("/upload")
    public ResponseEntity<Map<String, String>> uploadDocument(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            log.warn("Upload attempt with an empty file.");
            return ResponseEntity.badRequest().body(Map.of("message", "上传的文件不能为空。"));
        }
        try {
            log.info("开始处理上传的文件: {}", file.getOriginalFilename());
            documentIngestionService.processDocument(file.getResource());
            String successMessage = "文件处理成功: " + file.getOriginalFilename();
            log.info(successMessage);
            return ResponseEntity
                    .ok(Map.of("message", successMessage));
        } catch (Exception e) {
            log.error("处理文件失败: {}", file.getOriginalFilename(), e);
            String errorMessage = "处理文件时发生错误: " + e.getMessage();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", errorMessage));
        }
    }
}
