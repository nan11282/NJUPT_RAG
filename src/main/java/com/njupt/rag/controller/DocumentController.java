package com.njupt.rag.controller;

import com.njupt.rag.service.DocumentIngestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * 统一的文档管理控制器，负责手动上传文档。
 */
@Slf4j
@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
@CrossOrigin(origins = "*") // 允许所有来源的跨域请求，方便调试
public class DocumentController {
    @Autowired
    private final DocumentIngestionService documentIngestionService;

    /**
     * 处理文档上传，并将其交由 DocumentIngestionService 处理。
     *
     * @param file The uploaded file (e.g., PDF, Word document).
     * @return A response entity indicating the result of the operation.
     */
    @PostMapping("/upload")
    public ResponseEntity<Map<String, String>> uploadDocument(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            log.warn("Upload attempt with an empty file.");
            return ResponseEntity.badRequest().body(Map.of("message", "File cannot be empty."));
        }
        try {
            log.info("Processing uploaded file: {}", file.getOriginalFilename());
            documentIngestionService.processDocument(file.getResource());
            return ResponseEntity
                    .ok(Map.of("message", "Document processed successfully: " + file.getOriginalFilename()));
        } catch (Exception e) {
            log.error("Failed to process document: {}", file.getOriginalFilename(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Error processing document: " + e.getMessage()));
        }
    }
}
