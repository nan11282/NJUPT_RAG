package com.njupt.rag.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * RAG (Retrieval-Augmented Generation) 聊天服务。
 * <p>
 * 该服务整合了向量检索和大型语言模型（LLM）调用的完整流程：
 * 1. 接收用户问题。
 * 2. 在向量数据库中检索相关文档片段（知识库）。
 * 3. 将检索到的文档作为上下文，构建一个精确的系统提示（System Prompt）。
 * 4. 调用LLM，基于提供的上下文和用户问题生成回答。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagChatService {

    private final VectorStore vectorStore;
    private final ChatClient chatClient;

    /**
     * 定义了LLM的角色、任务和行为规范的系统提示模板。
     * - 角色定义: "柚子助手"，南京邮电大学的AI学长。
     * - 任务核心: 基于提供的背景信息（{documents}占位符）回答问题。
     * - 行为规范: 强调回答的严谨性、风格的亲切性，并要求在适当时使用Markdown格式。
     */
    private static final String SYSTEM_PROMPT = """
            角色: 你叫“柚子助手”, 是南京邮电大学的AI学长。
            任务: 基于以下提供的[背景信息](Context)回答同学关于保研、选课或学校政策的问题。
            规范:
            1. 严谨性: 如果Context里没有提及, 请直接说“相关文件里没找到”, 严禁编造。
            2. 风格: 亲切、鼓励, 使用南邮同学懂的黑话(如“通达”, “三牌楼”, “仙林”)。
            3. 格式: 如果涉及复杂的政策(如保研条件), 请用 Markdown 列表清晰展示。
            背景信息:
            {documents}
            """;

    /**
     * 处理非流式的聊天请求，一次性返回完整答案。
     *
     * @param userQuery 用户的原始问题
     * @return LLM生成的完整回答字符串
     */
    public String chat(String userQuery) {
        log.info("收到非流式聊天请求: '{}'", userQuery);
        // 1. 检索相关文档
        String documents = retrieveDocuments(userQuery);
        // 2. 构建系统提示
        var systemMessage = createSystemMessage(documents);

        log.debug("调用LLM以获取完整回答...");
        // 3. 调用LLM并返回结果
        return chatClient.prompt()
                .system(systemMessage.getContent())
                .user(userQuery)
                .call()
                .content();
    }

    /**
     * 处理流式聊天请求，通过SSE返回答案流。
     *
     * @param userQuery 用户的原始问题
     * @return 包含LLM生成内容的Flux流
     */
    public Flux<String> streamChat(String userQuery) {
        log.info("收到流式聊天请求: '{}'", userQuery);
        // 1. 检索相关文档
        String documents = retrieveDocuments(userQuery);
        // 2. 构建系统提示
        var systemMessage = createSystemMessage(documents);

        log.debug("调用LLM以获取流式回答...");
        // 3. 调用LLM并返回流式结果
        return chatClient.prompt()
                .system(systemMessage.getContent())
                .user(userQuery)
                .stream()
                .content();
    }

    /**
     * 根据用户问题从向量数据库中检索最相关的文档片段。
     *
     * @param userQuery 用户问题
     * @return 拼接后的文档内容字符串，各文档间以换行符分隔
     */
    private String retrieveDocuments(String userQuery) {
        // 设置检索请求，查询与用户问题最相似的TOP 4个文档片段
        SearchRequest searchRequest = SearchRequest.query(userQuery).withTopK(4);
        List<Document> similarDocuments = vectorStore.similaritySearch(searchRequest);
        log.debug("为问题 '{}' 找到了 {} 个相关的文档片段。", userQuery, similarDocuments.size());
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
}
