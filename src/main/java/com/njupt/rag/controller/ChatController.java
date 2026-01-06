package com.njupt.rag.controller;

import com.njupt.rag.service.RagChatService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.Map;

/**
 * 统一的聊天控制器，支持流式 (SSE) 和非流式 (REST) 响应。
 */
@RestController
@RequestMapping("/api/chat")
@CrossOrigin(origins = "*") // 允许所有来源的跨域请求，方便调试
public class ChatController {

    private final RagChatService ragChatService;

    @Autowired
    public ChatController(RagChatService ragChatService) {
        this.ragChatService = ragChatService;
    }

    /**
     * 处理流式聊天请求 (SSE)。
     *
     * @param question The user's question from the request parameter.
     * @return A Flux of strings, sent as a stream of events.
     */
    @GetMapping(value = "/stream", produces = "text/event-stream;charset=UTF-8")
    public Flux<String> streamChat(@RequestParam String question) {
        return ragChatService.streamChat(question);
    }

    /**
     * 处理非流式聊天请求。
     *
     * @param request The chat request containing the question.
     * @return A response entity with the complete answer.
     */
    @PostMapping
    public Map<String, String> chat(@RequestBody ChatRequest request) {
        String answer = ragChatService.chat(request.question());
        return Map.of("answer", answer);
    }

    /**
     * 用于映射传入 JSON 聊天请求的 Record。
     * 
     * @param question The user's question.
     */
    public record ChatRequest(String question) {
    }
}
