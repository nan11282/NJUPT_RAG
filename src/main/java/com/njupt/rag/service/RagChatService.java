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

@Slf4j
@Service
@RequiredArgsConstructor
public class RagChatService {

        private final VectorStore vectorStore;
        private final ChatClient chatClient;

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
         * Handles a user query and returns a single, complete response.
         *
         * @param userQuery The user's question.
         * @return A string containing the complete answer from the LLM.
         */
        public String chat(String userQuery) {
                log.info("Received chat request: '{}'", userQuery);
                String documents = retrieveDocuments(userQuery);
                var systemMessage = createSystemMessage(documents);

                log.debug("Calling LLM for a single response...");
                return chatClient.prompt()
                                .system(systemMessage.getContent())
                                .user(userQuery)
                                .call()
                                .content();
        }

        /**
         * Handles a user query and streams the response.
         *
         * @param userQuery The user's question.
         * @return A Flux of strings representing the streaming response from the LLM.
         */
        public Flux<String> streamChat(String userQuery) {
                log.info("Received streaming chat request: '{}'", userQuery);
                String documents = retrieveDocuments(userQuery);
                var systemMessage = createSystemMessage(documents);

                log.debug("Calling LLM for streaming response...");
                return chatClient.prompt()
                                .system(systemMessage.getContent())
                                .user(userQuery)
                                .stream()
                                .content();
        }

        private String retrieveDocuments(String userQuery) {
                SearchRequest searchRequest = SearchRequest.query(userQuery).withTopK(4);
                List<Document> similarDocuments = vectorStore.similaritySearch(searchRequest);
                log.debug("Found {} relevant document chunks for query: '{}'", similarDocuments.size(), userQuery);
                return similarDocuments.stream()
                                .map(Document::getContent)
                                .collect(Collectors.joining("\n\n"));
        }

        private org.springframework.ai.chat.messages.Message createSystemMessage(String documents) {
                SystemPromptTemplate systemPromptTemplate = new SystemPromptTemplate(SYSTEM_PROMPT);
                return systemPromptTemplate.createMessage(Map.of("documents", documents));
        }
}
