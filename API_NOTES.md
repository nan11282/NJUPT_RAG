# Spring AI API 使用说明

由于 Spring AI 1.0.0-SNAPSHOT 是快照版本，API 可能在不同版本间有变化。如果遇到编译错误，请根据实际的 Spring AI API 进行以下调整：

## 1. ChatModel / ChatClient

Spring AI 可能使用以下任一方式：

### 方式 A: 使用 ChatModel
```java
import org.springframework.ai.chat.ChatModel;
import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;

ChatResponse response = chatModel.call(prompt);
String answer = response.getResult().getOutput().getContent();
```

### 方式 B: 使用 ChatClient
```java
import org.springframework.ai.chat.client.ChatClient;

String answer = chatClient.prompt()
    .user(promptText)
    .call()
    .content();
```

### 方式 C: 使用 OpenAI ChatClient
```java
import org.springframework.ai.openai.OpenAiChatClient;

String answer = openAiChatClient.call(promptText);
```

## 2. Document API

Document 的内容获取方式可能不同：

```java
// 方式 A
String content = document.getContent();

// 方式 B
String content = document.getText();

// 方式 C
Object content = document.getContent();
String text = content != null ? content.toString() : "";
```

## 3. VectorStore 相似度搜索

```java
// 方式 A: 简单搜索
List<Document> docs = vectorStore.similaritySearch(query);

// 方式 B: 使用 SearchRequest
List<Document> docs = vectorStore.similaritySearch(
    SearchRequest.query(query).withTopK(5)
);

// 方式 C: 使用参数
List<Document> docs = vectorStore.similaritySearch(query, 5);
```

## 4. PgVectorStore 配置

```java
// 方式 A: Builder 模式
PgVectorStore.builder(jdbcTemplate, embeddingModel)
    .withDistanceType(...)
    .build();

// 方式 B: 构造函数
new PgVectorStore(jdbcTemplate, embeddingModel, options);

// 方式 C: 自动配置（推荐）
// 如果使用 spring-ai-pgvector-store-spring-boot-starter
// 可以直接注入 VectorStore，无需手动配置
```

## 建议

1. 查看 Spring AI 官方文档或示例代码
2. 检查实际依赖的 Spring AI 版本和 API
3. 根据编译错误信息调整导入和调用方式
4. 考虑使用 Spring AI 的自动配置功能

