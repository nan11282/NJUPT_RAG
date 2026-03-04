# NJUPTer RAG 应用

基于 Spring Boot 3 和 Spring AI 的 RAG（检索增强生成）应用，为南京邮电大学学生提供保研、选课和培养方案政策查询服务。

## 技术栈

- **Java**: 17+
- **框架**: Spring Boot 3.3+, Spring AI 1.0.0-SNAPSHOT
- **LLM**: DeepSeek V3 (兼容 OpenAI 协议)
- **向量库**: PostgreSQL + PgVector
- **工具**: Lombok, Docker Compose

## 功能特性

- 📚 文档上传和向量化存储
- 🔍 基于语义相似度的文档检索
- 💬 智能问答（保研、选课、培养方案）
- 🚀 RESTful API 接口

## 快速开始

### 前置要求

- JDK 17+
- Maven 3.6+
- Docker & Docker Compose
- DeepSeek API Key

### 重要提示

⚠️ **Spring AI 1.0.0-SNAPSHOT 是快照版本，API 可能在不同版本间有变化。**

如果遇到编译错误，请参考 `API_NOTES.md` 文件，根据实际的 Spring AI API 进行调整。主要可能需要调整的部分：

- ChatModel / ChatClient 的使用方式
- Document 的内容获取方法
- VectorStore 的相似度搜索 API
- PgVectorStore 的配置方式

### 启动步骤

1. **启动 PostgreSQL + PgVector**

```bash
docker-compose up -d
```

2. **配置环境变量**

设置 DeepSeek API Key：

```bash
export DEEPSEEK_API_KEY=your-deepseek-api-key
```

或在 `application.yml` 中直接配置。

3. **编译和运行**

```bash
mvn clean install
mvn spring-boot:run
```

4. **验证服务**

访问健康检查接口：

```bash
curl http://localhost:8080/api/policy/health
```

## API 接口

### 1. 通用查询

```bash
POST /api/policy/query
Content-Type: application/json

{
  "question": "保研需要什么条件？"
}
```

### 2. 保研政策查询

```bash
POST /api/policy/graduate-recommendation
Content-Type: application/json

{
  "question": "保研需要什么条件？"
}
```

### 3. 选课政策查询

```bash
POST /api/policy/course-selection
Content-Type: application/json

{
  "question": "如何选课？"
}
```

### 4. 培养方案查询

```bash
POST /api/policy/training-program
Content-Type: application/json

{
  "question": "计算机专业的培养方案是什么？"
}
```

### 5. 文档上传

```bash
POST /api/document/upload
Content-Type: multipart/form-data

file: [文档文件]
```

## 项目结构

```
src/
├── main/
│   ├── java/com/njupt/rag/
│   │   ├── config/          # 配置类
│   │   ├── controller/       # REST 控制器
│   │   ├── service/          # 业务服务层
│   │   └── NjupterRagApplication.java
│   └── resources/
│       ├── application.yml   # 主配置文件
│       ├── application-dev.yml
│       └── application-prod.yml
docker-compose.yml            # Docker Compose 配置
pom.xml                       # Maven 配置
```

## 配置说明

### 数据库配置

默认配置（`application.yml`）：
- 数据库：`njupter_rag`
- 用户名：`njupter`
- 密码：`njupter123`
- 端口：`5432`

### DeepSeek 配置

- API Key: 通过环境变量 `DEEPSEEK_API_KEY` 或配置文件设置
- Base URL: `https://api.deepseek.com`
- Model: `deepseek-chat`

### 向量存储配置

- 索引类型：HNSW
- 距离度量：余弦距离
- 向量维度：1536
- 相似度阈值：0.7

## 开发指南

### 添加文档

1. 准备政策文档（支持 PDF、Word、TXT 等格式）
2. 通过 `/api/document/upload` 接口上传
3. 文档会自动进行文本提取、分割和向量化

### 自定义查询

修改 `RagService` 中的提示词模板，可以调整回答的风格和格式。

## 许可证

MIT License

## 贡献

欢迎提交 Issue 和 Pull Request！

