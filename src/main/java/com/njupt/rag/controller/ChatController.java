package com.njupt.rag.controller;

import com.njupt.rag.service.RagChatService;
import com.njupt.rag.vo.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.Map;
import java.util.UUID;

/**
 * 聊天功能的API端点。
 * <p>
 * 提供了两种聊天方式：
 * 1. 流式响应 (SSE): 用于实现打字机效果的实时回答。
 * 2. 非流式响应: 一次性返回完整回答。
 * <p>
 * 新增功能：
 * - 支持文档过滤（用户选择自己的培养方案）
 * - 通过 targetDocument 参数指定检索范围
 */
@RestController
@RequestMapping("/api/chat")
@CrossOrigin(origins = "*")
public class ChatController {

    private final RagChatService ragChatService;

    @Autowired
    public ChatController(RagChatService ragChatService) {
        this.ragChatService = ragChatService;
    }

    /**
     * 处理流式聊天请求，使用 Server-Sent Events (SSE) 技术。
     * 支持多轮对话，通过 sessionId 维护会话历史。
     * 支持文档过滤，通过 targetDocument 参数指定检索范围。
     *
     * @param question     用户提出的问题
     * @param sessionId    会话 ID（可选，不提供则新建）
     * @param targetDocument 目标文档文件名（可选，如"2023级计算机学院培养方案.pdf"）
     * @return 返回一个字符串类型的 Flux 数据流，包含 sessionId 和回答流
     */
    @GetMapping(value = "/stream", produces = "text/event-stream;charset=UTF-8")
    public Flux<String> streamChat(
            @RequestParam String question,
            @RequestParam(required = false) String sessionId,
            @RequestParam(required = false) String targetDocument) {

        // 如果没有 sessionId，则生成新的
        if (sessionId == null || sessionId.isEmpty()) {
            sessionId = UUID.randomUUID().toString();
        }

        // 首先发送 sessionId 事件，让前端获取会话标识
        String sessionIdEvent = "event: sessionId\ndata: " + sessionId + "\n\n";

        // 获取回答流（传入文档过滤参数）
        Flux<String> answerFlux = ragChatService.streamChat(question, sessionId, targetDocument);

        // 合并 sessionId 事件和回答流
        return Flux.just(sessionIdEvent).concatWith(answerFlux);
    }

    /**
     * 处理非流式（一次性）聊天请求。
     * 支持多轮对话，通过 sessionId 维护会话历史。
     * 支持文档过滤，通过 targetDocument 参数指定检索范围。
     *
     * @param request 包含用户问题、会话 ID 和目标文档的请求体
     * @return 包含完整答案和 sessionId 的 Result 对象
     */
    @PostMapping
    public Result<Map<String, String>> chat(@RequestBody ChatRequest request) {
        String sessionId = request.sessionId();
        if (sessionId == null || sessionId.isEmpty()) {
            sessionId = UUID.randomUUID().toString();
        }

        String answer = ragChatService.chat(
            request.question(),
            sessionId,
            request.targetDocument()
        );
        return Result.success(Map.of("answer", answer, "sessionId", sessionId), "获取回答成功");
    }

    /**
     * 保存流式聊天的历史记录（已废弃）。
     * 流式聊天历史现在由服务端自动保存，不再需要前端调用此接口。
     * 保留此接口是为了向后兼容，返回成功但不执行任何操作。
     *
     * @param request 包含问题、回答和会话 ID 的请求体
     * @return 操作结果
     * @deprecated 流式聊天历史现在由服务端自动保存
     */
    @Deprecated
    @PostMapping("/save-history")
    public Result<String> saveHistory(@RequestBody SaveHistoryRequest request) {
        // 历史记录已由 streamChat() 方法自动保存，此处为空操作以保持向后兼容
        return Result.success("History is now automatically saved by server");
    }

    /**
     * 清除会话历史。
     *
     * @param sessionId 会话 ID
     * @return 操作结果
     */
    @DeleteMapping("/clear/{sessionId}")
    public Result<String> clearHistory(@PathVariable String sessionId) {
        ragChatService.clearHistory(sessionId);
        return Result.success("清除历史成功");
    }

    /**
     * 聊天请求体。
     *
     * @param question        用户的问题
     * @param sessionId       会话 ID（可选）
     * @param targetDocument   目标文档文件名（可选）
     */
    public record ChatRequest(
            String question,
            String sessionId,
            String targetDocument  // 新增：文档过滤参数
    ) {}

    /**
     * 保存历史请求体。
     *
     * @param question  用户的问题
     * @param answer    助手的回答
     * @param sessionId 会话 ID
     */
    public record SaveHistoryRequest(
            String question,
            String answer,
            String sessionId
    ) {}
}
