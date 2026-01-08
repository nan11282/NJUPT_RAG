package com.njupt.rag.controller;

import com.njupt.rag.service.RagChatService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.Map;

/**
 * 聊天功能的API端点。
 * <p>
 * 提供了两种聊天方式：
 * 1. 流式响应 (SSE): 用于实现打字机效果的实时回答。
 * 2. 非流式响应: 一次性返回完整回答。
 */
@RestController
@RequestMapping("/api/chat")
@CrossOrigin(origins = "*") // 允许所有来源的跨域请求，方便前后端分离开发和调试
public class ChatController {

    private final RagChatService ragChatService;

    @Autowired
    public ChatController(RagChatService ragChatService) {
        this.ragChatService = ragChatService;
    }

    /**
     * 处理流式聊天请求，使用 Server-Sent Events (SSE) 技术。
     *
     * @param question 用户提出的问题
     * @return 返回一个字符串类型的 Flux 数据流，每个元素是回答的一部分
     */
    @GetMapping(value = "/stream", produces = "text/event-stream;charset=UTF-8")
    public Flux<String> streamChat(@RequestParam String question) {
        return ragChatService.streamChat(question);
    }

    /**
     * 处理非流式（一次性）聊天请求。
     *
     * @param request 包含用户问题的请求体
     * @return 包含完整答案的 Map 对象
     */
    @PostMapping
    public Map<String, String> chat(@RequestBody ChatRequest request) {
        String answer = ragChatService.chat(request.question());
        return Map.of("answer", answer);
    }

    /**
     * 用于封装聊天请求体的简单数据类 (Record)。
     *
     * @param question 用户的问题
     */
    public record ChatRequest(String question) {
    }
}
