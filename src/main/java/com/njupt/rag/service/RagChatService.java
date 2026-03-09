package com.njupt.rag.service;

import com.njupt.rag.vo.MessageVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * RAG (Retrieval-Augmented Generation) 聊天服务。
 * <p>
 * 该服务整合了向量检索和大型语言模型（LLM）调用的完整流程：
 * 1. 接收用户问题。
 * 2. 在向量数据库中检索相关文档片段（知识库）。
 * 3. 将检索到的文档作为上下文，构建一个精确的系统提示（System Prompt）。
 * 4. 调用LLM，基于提供的上下文和用户问题生成回答。
 * <p>
 * 新增功能：
 * - 支持文档过滤，通过 targetDocument 参数指定检索范围
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagChatService {

    private final VectorStore vectorStore;
    private final ChatClient chatClient;
    private final QueryRewriteService queryRewriteService;
    private final HybridSearchService hybridSearchService;
    private final ConversationHistoryService conversationHistoryService;

    /**
     * 定义了LLM的角色、任务和行为规范的系统提示模板。
     * - 角色定义: "柚子助手"，南京邮电大学的AI学长。
     * - 任务核心: 基于提供的背景信息（{documents}占位符）回答问题。
     * - 行为规范: 强调回答的严谨性、风格的亲切性，并要求在适当时使用Markdown格式。
     */
    private static final String SYSTEM_PROMPT = """
            角色: 你叫"柚子助手", 是南京邮电大学的AI学长。
            任务: 基于以下提供的[背景信息](Context)回答同学关于保研、选课或学校政策的问题。
            规范:
            1. 严谨性: 如果Context里没有提及, 请直接说"相关文件里没找到", 严禁编造。
            2. 风格: 亲切、鼓励, 使用南邮同学懂的黑话(如"通达", "三牌楼", "仙林")。
            3. 格式: 如果涉及复杂的政策(如保研条件), 请用 Markdown 列表清晰展示。
            背景信息:
            {documents}
            """;

    /**
     * 处理非流式的聊天请求，一次性返回完整答案。
     * 支持文档过滤，通过 targetDocument 参数指定检索范围。
     *
     * @param userQuery       用户的原始问题
     * @param sessionId       会话 ID
     * @param targetDocument   目标文档文件名（可选，如"2023级计算机学院培养方案.pdf"）
     * @return LLM生成的完整回答字符串
     */
    public String chat(String userQuery, String sessionId, String targetDocument) {
        log.info("收到非流式聊天请求: '{}', sessionId: '{}', targetDocument: '{}'",
            userQuery, sessionId, targetDocument);

        // 1. 改写查询
        String rewrittenQuery = queryRewriteService.rewriteQuery(userQuery);

        // 2. 检索相关文档（传入文档过滤参数）
        String documents = retrieveDocuments(rewrittenQuery, targetDocument);

        // 3. 获取会话历史
        List<MessageVO> history = conversationHistoryService.getHistory(sessionId);

        log.debug("调用LLM以获取完整回答，历史消息数: {}", history.size());

        // 4. 构建完整 Prompt 并调用 LLM
        Prompt prompt = buildPrompt(documents, history, userQuery);
        String answer = chatClient.prompt(prompt).call().content();

        // 5. 保存当前对话到历史
        conversationHistoryService.addMessage(sessionId, MessageVO.user(userQuery));
        conversationHistoryService.addMessage(sessionId, MessageVO.assistant(answer));

        return answer;
    }

    /**
     * 处理流式聊天请求，通过SSE返回答案流。
     * 支持文档过滤，通过 targetDocument 参数指定检索范围。
     *
     * @param userQuery       用户的原始问题
     * @param sessionId       会话 ID
     * @param targetDocument   目标文档文件名（可选，如"2023级计算机学院培养方案.pdf"）
     * @return 包含LLM生成内容的Flux流
     */
    public Flux<String> streamChat(String userQuery, String sessionId, String targetDocument) {
        log.info("收到流式聊天请求: '{}', sessionId: '{}', targetDocument: '{}'",
            userQuery, sessionId, targetDocument);

        // 1. 改写查询
        String rewrittenQuery = queryRewriteService.rewriteQuery(userQuery);

        // 2. 检索相关文档（传入文档过滤参数）
        String documents = retrieveDocuments(rewrittenQuery, targetDocument);

        // 3. 获取会话历史
        List<MessageVO> history = conversationHistoryService.getHistory(sessionId);

        log.debug("调用LLM以获取流式回答，历史消息数: {}", history.size());

        // 4. 构建完整 Prompt
        Prompt prompt = buildPrompt(documents, history, userQuery);

        // 5. 使用 AtomicReference 收集完整响应回答到历史
        AtomicReference<StringBuilder> fullAnswerBuilder = new AtomicReference<>(new StringBuilder());

        return chatClient.prompt(prompt)
                .stream()
                .content()
                .doOnNext(chunk -> {
                    // 每次收到数据块时，追加到完整回答中
                    fullAnswerBuilder.get().append(chunk);
                })
                .doOnComplete(() -> {
                    // 流式响应完成后，自动保存完整回答到历史
                    String fullAnswer = fullAnswerBuilder.get().toString();
                    conversationHistoryService.addMessage(sessionId, MessageVO.user(userQuery));
                    conversationHistoryService.addMessage(sessionId, MessageVO.assistant(fullAnswer));
                    log.info("流式回答完成并已保存到历史，sessionId: {}, 回答长度: {}", sessionId, fullAnswer.length());
                });
    }

    /**
     * 清除会话历史。
     *
     * @param sessionId 会话 ID
     */
    public void clearHistory(String sessionId) {
        conversationHistoryService.clearHistory(sessionId);
        log.info("会话历史已清除，sessionId: {}", sessionId);
    }

    /**
     * 根据用户问题从向量数据库中检索最相关的文档片段。
     * 使用混合检索（向量检索 + 全文检索 + RRF）。
     * 支持文档过滤。
     *
     * @param userQuery       用户问题
     * @param targetDocument   目标文档文件名（可选）
     * @return 拼接后的文档内容字符串，各文档间以换行符分隔
     */
    private String retrieveDocuments(String userQuery, String targetDocument) {
        // 使用混合检索（传入文档过滤参数）
        List<Document> similarDocuments = hybridSearchService.hybridSearch(userQuery, targetDocument);

        log.debug("为问题 '{}' 通过混合检索找到了 {} 个相关的文档片段（targetDocument: {}）",
            userQuery, similarDocuments.size(), targetDocument);

        // 将文档内容拼接成一个字符串，作为LLM的上下文
        return similarDocuments.stream()
                .map(Document::getContent)
                .collect(Collectors.joining("\n\n"));
    }

    /**
     * 使用检索到的文档内容填充系统提示模板。
     *
     * @param documents 检索到的文档内容字符串
     * @return 填充了上下文的系统消息对象
     */
    private org.springframework.ai.chat.messages.Message createSystemMessage(String documents) {
        SystemPromptTemplate systemPromptTemplate = new SystemPromptTemplate(SYSTEM_PROMPT);
        return systemPromptTemplate.createMessage(Map.of("documents", documents));
    }

    /**
     * 构建完整的 Prompt，包含系统提示、历史对话和当前问题。
     * Prompt 结构：[系统提示] + [历史对话] + [当前问题]
     *
     * @param documents 检索到的文档内容
     * @param history   会话历史
     * @param userQuery 当前用户问题
     * @return 完整的 Prompt 对象
     */
    private Prompt buildPrompt(String documents, List<MessageVO> history, String userQuery) {
        // 构建历史对话字符串
        StringBuilder historyBuilder = new StringBuilder();
        if (!history.isEmpty()) {
            historyBuilder.append("\n\n【历史对话】\n");
            for (MessageVO msg : history) {
                if (MessageVO.ROLE_USER.equals(msg.getRole())) {
                    historyBuilder.append("用户: ").append(msg.getContent()).append("\n");
                } else {
                    historyBuilder.append("助手: ").append(msg.getContent()).append("\n");
                }
            }
        }

        // 构建完整系统提示
        SystemPromptTemplate systemPromptTemplate = new SystemPromptTemplate(SYSTEM_PROMPT);
        org.springframework.ai.chat.messages.Message systemMessage = systemPromptTemplate.createMessage(
                Map.of("documents", documents));

        // 构建用户消息（历史对话 + 当前问题）
        String userMessage = historyBuilder.toString() + "\n\n【当前问题】\n" + userQuery;

        // 使用 Prompt 类直接构建
        List<org.springframework.ai.chat.messages.Message> messages = new ArrayList<>();
        messages.add(systemMessage);
        messages.add(new org.springframework.ai.chat.messages.UserMessage(userMessage));
        return new Prompt(messages);
    }
}
