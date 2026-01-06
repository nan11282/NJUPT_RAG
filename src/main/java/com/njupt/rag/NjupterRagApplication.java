package com.njupt.rag;

import org.springframework.ai.autoconfigure.openai.OpenAiAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import org.springframework.ai.autoconfigure.vectorstore.pgvector.PgVectorStoreAutoConfiguration;

// 👇 2. 在 @SpringBootApplication 注解里加上 exclude
// 这句话的意思是：屏蔽掉官方的自动配置，只用我自己写的 AiConfig
@SpringBootApplication(exclude = {
        PgVectorStoreAutoConfiguration.class, // 屏蔽向量库自动配置 (我们手动配了)
        OpenAiAutoConfiguration.class         // 👈 屏蔽 OpenAI 自动配置 (我们也手动配了)
})
public class NjupterRagApplication {

    public static void main(String[] args) {
        System.setProperty("http.proxyHost", "");
        System.setProperty("http.proxyPort", "");
        System.setProperty("https.proxyHost", "");
        System.setProperty("https.proxyPort", "");
        try {
            String dsKey = "sk-96d1cf055bec4712bca304802d5a6520"; // ⚠️ 替换这里！
            String dsBody = """
                {
                    "model": "deepseek-chat",
                    "messages": [{"role": "user", "content": "Hello"}]
                }
                """;
            var client = java.net.http.HttpClient.newHttpClient();
            var request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create("https://api.deepseek.com/chat/completions"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + dsKey)
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString(dsBody))
                    .build();
            var response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                System.out.println("✅ [DeepSeek] 连接成功！回复片段: " + response.body().substring(0, Math.min(50, response.body().length())) + "...");
            } else {
                System.out.println("❌ [DeepSeek] 连接失败: " + response.statusCode() + " " + response.body());
            }
        } catch (Exception e) { System.out.println("❌ [DeepSeek] 异常: " + e.getMessage()); }
        // 👆👆👆 DeepSeek 测试结束 👆👆👆

        // 👇👇👇 2. 插入原生测试代码 (开始) 👇👇👇
        System.out.println("🚀 [原生测试] 开始测试硅基流动连接...");
        try {
            String apiKey = "sk-gpiqjshszflyacfvulvatsgbtbjteypknfjcdbwzyahoyiea"; // ⚠️ 替换这里！
            String jsonBody = """
                {
                    "model": "BAAI/bge-m3",
                    "input": "test"
                }
                """;

            var client = java.net.http.HttpClient.newHttpClient();
            var request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create("https://api.siliconflow.cn/v1/embeddings"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            var response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());

            System.out.println("📡 [原生测试] 状态码: " + response.statusCode());
            System.out.println("📄 [原生测试] 响应体: " + response.body());

            if (response.statusCode() == 200) {
                System.out.println("✅ [原生测试] Java 原生连接成功！问题出在 Spring 配置上。");
            } else {
                System.out.println("❌ [原生测试] Java 原生连接失败！问题出在 Java 网络环境上。");
                return; // 如果原生都挂了，启动 Spring 也没意义，直接退出
            }

        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
        // 👆👆👆 2. 插入原生测试代码 (结束) 👆👆👆


        SpringApplication.run(NjupterRagApplication.class, args);
    }

}
