package com.njupt.rag.controller;

import com.njupt.rag.config.DocumentConfig;
import com.njupt.rag.service.DocumentIngestionService;
import com.njupt.rag.vo.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 文档管理相关的API端点。
 * <p>
 * 1. 提供文档列表，支持用户选择自己的学院培养方案
 * 2. 支持文档上传操作
 */
@Slf4j
@RestController
@RequestMapping("/api/document")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class DocumentController {

    private final DocumentIngestionService documentIngestionService;
    private final DocumentConfig documentConfig;

    /**
     * 获取所有可用的文档列表。
     * <p>
     * 从 application.yml 的 njupter.documents 配置中读取。
     * 前端使用此接口动态渲染文档选择下拉框。
     *
     * @return 文档信息列表
     */
    @GetMapping("/list")
    public Result<List<DocumentConfig.DocumentInfo>> getDocumentList() {
        return Result.success(documentConfig.getItems(), "获取文档列表成功");
    }

    /**
     * 接收并处理上传的文档文件。
     * <p>
     * 文件将被传递给 {@link DocumentIngestionService} 进行后续的解析、切分和向量化处理。
     *
     * @param file 用户通过 multipart/form-data 方式上传的文件
     * @return 操作结果
     */
    @PostMapping("/upload")
    public Result<String> uploadDocument(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            log.warn("上传尝试空文件");
            return Result.error("上传的文件不能为空");
        }

        try {
            log.info("开始处理上传的文件: {}", file.getOriginalFilename());
            documentIngestionService.processDocument(file.getResource());
            String successMessage = "文件处理成功: " + file.getOriginalFilename();
            log.info(successMessage);
            return Result.success(successMessage);
        } catch (Exception e) {
            log.error("处理文件失败: {}", file.getOriginalFilename(), e);
            return Result.error("处理文件时发生错误: " + e.getMessage());
        }
    }
}
