package com.njupt.rag.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 聊天消息值对象。
 * <p>
 * 用于存储和传输会话中的对话消息。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MessageVO {

    /**
     * 消息角色：USER（用户）或 ASSISTANT（助手）
     */
    private String role;

    /**
     * 消息内容
     */
    private String content;

    /**
     * 消息时间戳（用于排序）
     */
    private long timestamp;

    public MessageVO(String role, String content) {
        this.role = role;
        this.content = content;
        this.timestamp = System.currentTimeMillis();
    }

    /**
     * 消息角色常量
     */
    public static final String ROLE_USER = "user";
    public static final String ROLE_ASSISTANT = "assistant";

    /**
     * 创建用户消息
     */
    public static MessageVO user(String content) {
        return new MessageVO(ROLE_USER, content);
    }

    /**
     * 创建助手消息
     */
    public static MessageVO assistant(String content) {
        return new MessageVO(ROLE_ASSISTANT, content);
    }
}
