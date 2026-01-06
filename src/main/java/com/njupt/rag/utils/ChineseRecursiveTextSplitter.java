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
 * 专为中文 RAG 优化的递归文本切分器。
 * <p>
 * 策略：
 * 1. 优先尝试按“段落”（双换行）切分。
 * 2. 其次按“行”（单换行）切分（这对表格行非常重要）。
 * 3. 再次按“句子”（句号、感叹号等）切分。
 * 4. 然后按“短语”（逗号、分号）切分。
 * 5. 最后按“单词/字”（空格、制表符）切分（这对表格列非常重要）。
 * 6. 兜底：如果以上都无法将文本切小，则强制按字符数截断。
 */
public class ChineseRecursiveTextSplitter extends TextSplitter {

    private static final Logger logger = LoggerFactory.getLogger(ChineseRecursiveTextSplitter.class);

    private final int chunkSize;
    private final int chunkOverlap;

    // 分隔符优先级列表
    private final List<String> separators;

    /**
     * @param chunkSize    目标块大小（字符数）。建议 500-800。
     * @param chunkOverlap 块之间的重叠部分（字符数）。建议 50-100。
     */
    public ChineseRecursiveTextSplitter(int chunkSize, int chunkOverlap) {
        this.chunkSize = chunkSize;
        this.chunkOverlap = chunkOverlap;
        // 初始化分隔符优先级，越靠前优先级越高
        this.separators = Arrays.asList(
                "\n\n",       // 1. 物理段落
                "\n",         // 2. 换行（表格的一行通常以此结束）
                "。", "！", "？", "!", "?", // 3. 句子结束
                "；", ";",    // 4. 分号
                "，", ",",    // 5. 逗号
                " ", "\t"     // 6. 空格和制表符（专门应对表格列分割）
        );
    }

    @Override
    public List<Document> apply(List<Document> documents) {
        List<Document> splitDocuments = new ArrayList<>();
        for (Document doc : documents) {
            String content = doc.getContent();
            if (content == null || content.isEmpty()) {
                continue;
            }

            // 调用内部核心切分逻辑
            List<String> chunks = splitText(content, this.separators);

            for (String chunk : chunks) {
                // 关键：保留原文档的 Metadata（页码、文件名等），这对引用来源至关重要
                Document newDoc = new Document(chunk, doc.getMetadata());
                splitDocuments.add(newDoc);
            }
        }
        return splitDocuments;
    }

    /**
     * 实现父类 TextSplitter 的抽象方法。
     * 虽然我们的 apply 方法已经覆盖了逻辑，但实现此方法可以保证父类契约完整，
     * 防止框架其他部分直接调用此方法时出错。
     */
    @Override
    protected List<String> splitText(String text) {
        return splitText(text, this.separators);
    }

    /**
     * 递归切分文本的核心私有方法
     *
     * @param text       要切分的文本
     * @param separators 当前可用的分隔符列表（递归过程中会越来越少）
     * @return 切分后的文本块列表
     */
    private List<String> splitText(String text, List<String> separators) {
        List<String> finalChunks = new ArrayList<>();

        // 1. 终止条件：如果文本已经小于 chunkSize，直接返回
        if (text.length() <= chunkSize) {
            finalChunks.add(text);
            return finalChunks;
        }

        // 2. 寻找最佳分隔符
        String separator = null;
        List<String> nextSeparators = new ArrayList<>();

        // 找到当前列表中第一个存在于文本中的分隔符
        for (int i = 0; i < separators.size(); i++) {
            String s = separators.get(i);
            if (text.contains(s)) {
                separator = s;
                // 剩余的分隔符用于下一级递归
                nextSeparators = separators.subList(i + 1, separators.size());
                break;
            }
        }

        // 3. 兜底逻辑：如果找不到任何分隔符（比如长串乱码或纯数字）
        if (separator == null) {
            return bruteForceSplit(text);
        }

        // 4. 执行切分
        // 使用 Pattern.quote 避免分隔符中的特殊字符（如?）干扰正则
        String[] rawSplits = text.split(Pattern.quote(separator));

        // 5. 合并碎片（Merge）
        List<String> goodSplits = new ArrayList<>();
        StringBuilder currentChunk = new StringBuilder();

        for (String split : rawSplits) {
            // 如果片段是空的（例如连续空格），直接跳过逻辑处理，但可能需要补分隔符
            if (split.isEmpty() && currentChunk.length() > 0) {
                // 这种情况通常是连续分隔符，比如两个空格。
                // 简单的策略是只追加分隔符，不追加内容
                if(currentChunk.length() + separator.length() <= chunkSize) {
                    currentChunk.append(separator);
                }
                continue;
            }

            // 检查：如果 (当前块 + 分隔符 + 新片段) 超过限制
            if (currentChunk.length() + separator.length() + split.length() > chunkSize) {
                // A. 保存当前积攒的块
                if (currentChunk.length() > 0) {
                    goodSplits.add(currentChunk.toString());
                    currentChunk.setLength(0);
                }

                // B. 处理新片段
                if (split.length() > chunkSize) {
                    // 如果新片段本身依然超长，递归调用下一级分隔符
                    List<String> subChunks = splitText(split, nextSeparators);
                    goodSplits.addAll(subChunks);
                } else {
                    // 如果新片段能放下，就开始新的 currentChunk
                    currentChunk.append(split);
                }
            } else {
                // C. 未超过限制，追加到当前块
                if (currentChunk.length() > 0) {
                    currentChunk.append(separator); // 补回分隔符，保持语义连贯
                }
                currentChunk.append(split);
            }
        }

        // 处理最后一个遗留的块
        if (currentChunk.length() > 0) {
            goodSplits.add(currentChunk.toString());
        }

        return goodSplits;
    }

    /**
     * 暴力切分：当没有任何分隔符可用，但文本依然超长时使用。
     * 按固定长度强制截断，确保绝对不会报错。
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