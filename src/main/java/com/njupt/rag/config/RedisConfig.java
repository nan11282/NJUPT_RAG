package com.njupt.rag.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.njupt.rag.vo.MessageVO;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.util.List;

/**
 * Redis 配置类。
 * <p>
 * 配置 RedisTemplate 的序列化方式，支持存储 List<MessageVO> 类型的会话历史。
 */
@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, List<MessageVO>> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, List<MessageVO>> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // 使用 String 序列化器作为 key 的序列化器
        template.setKeySerializer(new StringRedisSerializer());

        // 使用 JSON 序列化器作为 value 的序列化器
        template.setValueSerializer(jsonSerializer());

        // 使用 String 序列化器作为 hash key 的序列化器
        template.setHashKeySerializer(new StringRedisSerializer());

        // 使用 JSON 序列化器作为 hash value 的序列化器
        template.setHashValueSerializer(jsonSerializer());

        template.afterPropertiesSet();
        return template;
    }

    /**
     * 创建 JSON 序列化器，支持类型信息。
     */
    private RedisSerializer<Object> jsonSerializer() {
        ObjectMapper objectMapper = new ObjectMapper();
        // 允许所有类型进行反序列化
        objectMapper.activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL
        );
        return new GenericJackson2JsonRedisSerializer(objectMapper);
    }
}
