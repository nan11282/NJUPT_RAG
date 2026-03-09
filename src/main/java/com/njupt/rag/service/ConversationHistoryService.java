package com.njupt.rag.service;

import com.njupt.rag.vo.MessageVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 会话历史服务。
 * <p>
 * 使用 Redis 存储会话历史，支持多轮对话场景。
 */
@Slf4j
@Service
@RequiredArgsConstructshunor
public class ConversationHistoryService {

    private final RedisTemplate<String, List<MessageVO>> redisTemplate;

    private static final String SESSION_KEY_PREFIX = "chat:session:";
    private static final int MAX_HISTORY_SIZE = 5;
    private static final int MAX_TOKENS = 2000;
    private static final Duration SESSION_TTL = Duration.ofHours(2);

    public ConversationHistoryService(RedisTemplate<String, List<MessageVO>> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 获取会话历史。
     *
     * @param sessionId 会话 ID
     * @return 会话历史列表
     */
    public List<MessageVO> getHistory(String sessionId) {
        String key = buildKey(sessionId);
        List<MessageVO> history = redisTemplate.opsForValue().get(key);
        return history != null ? history : new ArrayList<>();
    }

    /**
     * 添加消息到会话历史。
     * 保持最近的 MAX_HISTORY_SIZE 轮对话，并控制 token 数量。
     *
     * @param sessionId 会话 ID
     * @param message   消息
     */
    public void addMessage(String sessionId, MessageVO message) {
        String key = buildKey(sessionId);
        List<MessageVO> history = getHistory(sessionId);

        // 添加新消息
        history.add(message);

        // 控制 token 数量
        history = truncateByTokens(history, MAX_TOKENS);

        // 保持最近 MAX_HISTORY_SIZE 轮对话（每轮对话包含用户消息和助手消息）
        int maxMessages = MAX_HISTORY_SIZE * 2;
        if (history.size() > maxMessages) {
            history = history.subList(history.size() - maxMessages, history.size());
        }

        // 保存到 Redis
        redisTemplate.opsForValue().set(key, history, SESSION_TTL);
        log.debug("会话 {} 更新完成，当前历史大小: {}", sessionId, history.size());
    }

    /**
     * 根据消息内容估算 token 数量（简化版）。
     * 对于中文，大约 1 个字符 ≈ 0.6-0.7 token，这里使用 0.7 作为估算系数。
     *
     * @param text 文本
     * @return 估算的 token 数量
     */
    private int estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        // 简化估算：中文字符 * 0.7 + 英文字符 * 0.3
        return (int) (text.chars().filter(c -> c > 127).count() * 0.7 +
                      text.chars().filter(c -> c <= 127).count() * 0.3);
    }

    /**
     * 截断消息列表，使总 token 数量不超过限制。
     * 从最早的消息开始截断。
     *
     * @param messages  消息列表
     * @param maxTokens 最大 token 数量
     * @return 截断后的消息列表
     */
    private List<MessageVO> truncateByTokens(List<MessageVO> messages, int maxTokens) {
        List<MessageVO> result = new ArrayList<>(messages);
        int totalTokens = result.stream()
                .mapToInt(msg -> estimateTokens(msg.getContent()))
                .sum();

        // 从最早的开始删除，直到 token 数量符合要求
        while (!result.isEmpty() && totalTokens > maxTokens && result.size() > 2) {
            // 保留最后 2 条消息（最近一轮对话）
            MessageVO removed = result.remove(0);
            totalTokens -= estimateTokens(removed.getContent());
            log.debug("截断消息: role={}, tokens={}", removed.getRole(), estimateTokens(removed.getContent()));
        }

        return result;
    }

    /**
     * 构建 Redis key。
     */
    private String buildKey(String sessionId) {
        return SESSION_KEY_PREFIX + sessionId;
    }

    /**
     * 清除会话历史。
     */
    public void clearHistory(String sessionId) {
        String key = buildKey(sessionId);
        redisTemplate.delete(key);
        log.debug("会话 {} 已清除", sessionId);
    }
}
