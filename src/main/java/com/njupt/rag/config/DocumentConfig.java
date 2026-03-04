package com.njupt.rag.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 文档配置类。
 * <p>
 * 用于配置可用的文档列表，支持用户选择自己的学院培养方案。
 * 在 application.yml 中配置。
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "njupter.documents")
public class DocumentConfig {

    /**
     * 文档列表。
     * 可在 application.yml 中配置：
     * <pre>
     * njupter:
     *   documents:
     *     - filename: "2023级计算机学院培养方案.pdf"
     *       name: "计算机学院"
     *       category: "培养方案"
     *     - filename: "2023级通信学院培养方案.pdf"
     *       name: "通信与信息工程学院"
     *       category: "培养方案"
     * </pre>
     */
    private List<DocumentInfo> items = new ArrayList<>();

    /**
     * 文档信息类。
     */
    @Getter
    @Setter
    public static class DocumentInfo {
        /**
         * 文件名（用于文档过滤）
         */
        private String filename;

        /**
         * 显示名称（在前端下拉框中显示）
         */
        private String name;

        /**
         * 文档类别（培养方案、保研政策、选课指南等）
         */
        private String category;
    }
}
