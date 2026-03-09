package com.njupt.rag.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 文档配置属性类。
 * 从 application.yml 文件中读取 `njupter` 前缀的配置。
 * 用于控制文档存储路径和可用文档列表。
 */
@Setter
@Getter
@Component
@ConfigurationProperties(prefix = "njupter")
public class DocumentProperties {

    /**
     * 文档路径配置，支持多个路径（逗号分隔）。
     * 格式：
     * - classpath*:data/*.pdf        从 classpath 扫描
     * - file:./data/*.pdf            从项目根目录扫描
     * - file:/var/data/*.pdf         从绝对路径扫描
     * - classpath:...,file:...      同时扫描多个位置
     */
    private String documentPaths = "classpath*:data/*.pdf";

    /**
     * 解析文档路径配置，返回路径列表。
     * 支持逗号分隔的多路径配置。
     *
     * @return 文档路径列表
     */
    public List<String> getDocumentPathList() {
        if (documentPaths == null || documentPaths.trim().isEmpty()) {
            return new ArrayList<>();
        }

        String[] paths = documentPaths.split(",");
        List<String> result = new ArrayList<>();
        for (String path : paths) {
            String trimmed = path.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }
}
