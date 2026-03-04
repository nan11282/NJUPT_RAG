package com.njupt.rag.utils;

import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TextSplitter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 面向中文文本的递归文本切分器。
 * <p>
 * 目标：在不破坏语义的前提下，将长文本拆分为更小的片段，方便后续向量化处理。
 * <p>
 * 切分优先级（从高到低）：
 * 1. 段落（两个换行）
 * 2. 行（单换行）
 * 3. 句子（句号、感叹号、问号）
 * 4. 短语（分号）
 * 5. 子句（逗号）
 * 6. 单词/字符（空格、Tab）
 * 7. 兜底：固定长度截断
 */
public class ChineseRecursiveTextSplitter extends TextSplitter {

    private static final Logger logger = LoggerFactory.getLogger(ChineseRecursiveTextSplitter.class);

    private final int chunkSize;
    private final int chunkOverlap;

    // 分隔符优先级列表（越靠前优先级越高）
    private final List<String> separators;

    /**
     * @param chunkSize    目标块长度（单位：字符）。
     * @param chunkOverlap 相邻块之间的重叠长度（单位：字符）。
     */
    public ChineseRecursiveTextSplitter(int chunkSize, int chunkOverlap) {
        this.chunkSize = chunkSize;
        this.chunkOverlap = chunkOverlap;
        this.separators = Arrays.asList(
                "\n\n",                 // 段落
                "\n",                   // 换行
                "。", "！", "？", "!", "?",  // 句子结束符
                "；", ";",               // 分号
                "，", ",",               // 逗号
                " ", "\t"               // 空格、制表符
        );
    }

    /* ============================= 公开接口 ============================= */

    @Override
    public List<Document> apply(List<Document> documents) {
        List<Document> splitDocuments = new ArrayList<>();
        for (Document doc : documents) {
            String content = doc.getContent();
            if (content == null || content.isEmpty()) {
                continue;
            }
            // 调用核心切分逻辑
            List<String> chunks = splitText(content, this.separators);
            // 将分割后的文本与原始元数据重新封装为 Document
            for (String chunk : chunks) {
                splitDocuments.add(new Document(chunk, doc.getMetadata()));
            }
        }
        return splitDocuments;
    }

    /**
     * 覆写抽象方法，防止框架其他地方直接调用时出错。
     */
    @Override
    protected List<String> splitText(String text) {
        return splitText(text, this.separators);
    }

    /* ============================= 核心实现 ============================= */

    /**
     * 递归切分核心方法。
     * <p>
     * 核心改进：实现 chunkOverlap 功能，在保存块时保留最后 chunkOverlap 个字符作为下一个块的前缀。
     */
    private List<String> splitText(String text, List<String> separators) {
        return splitText(text, separators, "");
    }

    /**
     * 递归切分核心方法（带重叠前缀）。
     *
     * @param text           要切分的文本
     * @param separators     可用的分隔符列表（按优先级排序）
     * @param overlapPrefix  上一个块的重叠部分（作为当前块的前缀）
     * @return 切分后的文本块列表
     */
    private List<String> splitText(String text, List<String> separators, String overlapPrefix) {
        List<String> finalChunks = new ArrayList<>();

        // 1. 如果有重叠前缀，先加到文本前面
        String effectiveText = overlapPrefix + text;

        // 2. 终止条件：文本已足够短
        if (effectiveText.length() <= chunkSize) {
            finalChunks.add(effectiveText);
            return finalChunks;
        }

        // 3. 选择当前可用分隔符中，文本内首次出现的分隔符
        String separator = null;
        List<String> nextSeparators = new ArrayList<>();
        for (int i = 0; i < separators.size(); i++) {
            String s = separators.get(i);
            // 注意：在 effectiveText 中查找分隔符，但需要跳过 overlapPrefix 部分
            int idx = effectiveText.indexOf(s, overlapPrefix.length());
            if (idx >= 0) {
                separator = s;
                nextSeparators = separators.subList(i + 1, separators.size());
                break;
            }
        }

        // 4. 无分隔符可用：兜底暴力切分
        if (separator == null) {
            return bruteForceSplit(effectiveText);
        }

        // 5. 使用找到的分隔符进行初步切分
        // 注意：只切分 overlapPrefix 之后的部分
        String textToSplit = effectiveText.substring(overlapPrefix.length());
        String[] rawSplits = textToSplit.split(Pattern.quote(separator));

        // 6. 合并碎片，确保每块尽可能接近 chunkSize
        List<String> goodSplits = new ArrayList<>();
        StringBuilder currentChunk = new StringBuilder(overlapPrefix);

        for (String split : rawSplits) {
            if (split.isEmpty() && currentChunk.length() > 0) {
                if (currentChunk.length() + separator.length() <= chunkSize) {
                    currentChunk.append(separator);
                }
                continue;
            }

            if (currentChunk.length() + separator.length() + split.length() > chunkSize) {
                // 当前块已满 -> 先保存（保留最后 chunkOverlap 个字符作为重叠）
                if (currentChunk.length() > 0) {
                    String chunkToSave = currentChunk.toString();
                    goodSplits.add(chunkToSave);

                    // 提取重叠部分：最后 chunkOverlap 个字符
                    String nextOverlap = "";
                    if (chunkOverlap > 0 && chunkToSave.length() > chunkOverlap) {
                        nextOverlap = chunkToSave.substring(chunkToSave.length() - chunkOverlap);
                    }

                    // 清空当前块，准备下一个
                    currentChunk.setLength(0);
                    currentChunk.append(nextOverlap);
                }

                // 超长片段 -> 递归继续切
                if (split.length() > chunkSize) {
                    // 递归调用时，传入当前的重叠前缀
                    List<String> recursiveChunks = splitText(split, nextSeparators, currentChunk.toString());
                    goodSplits.addAll(recursiveChunks);
                    currentChunk.setLength(0);  // 递归处理完了，清空
                } else {
                    // 非超长片段 -> 直接添加到当前块
                    if (currentChunk.length() > 0) {
                        currentChunk.append(separator);
                    }
                    currentChunk.append(split);
                }
            } else {
                // 块未满 -> 继续累积
                if (currentChunk.length() > 0) {
                    currentChunk.append(separator);
                }
                currentChunk.append(split);
            }
        }

        // 补上最后一个块
        if (currentChunk.length() > 0) {
            goodSplits.add(currentChunk.toString());
        }

        return goodSplits;
    }

    /**
     * 当无任何分隔符可用时，按固定长度强制分割。
     */
    private List<String> bruteForceSplit(String text) {
        List<String> chunks = new ArrayList<>();
        int length = text.length();
        int start = 0;
        while (start < length) {
            int end = Math.min(start + chunkSize, length);
            chunks.add(text.substring(start, end));
            if (start + chunkSize >= length) {
                break;
            }
            start += (chunkSize - chunkOverlap);
        }
        return chunks;
    }
}
