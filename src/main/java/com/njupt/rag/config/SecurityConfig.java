package com.njupt.rag.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

/**
 * Spring Security 配置类。
 * <p>
 * 负责配置应用的认证、授权、CORS等安全策略。
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * 配置安全过滤链，定义API的访问权限和安全策略。
     *
     * @param http HttpSecurity 配置对象
     * @return SecurityFilterChain 实例
     * @throws Exception 配置过程中可能抛出的异常
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // 启用CORS（跨域资源共享）
                .cors(Customizer.withDefaults())
                // 禁用CSRF（跨站请求伪造）保护，适用于无状态的API
                .csrf(csrf -> csrf.disable())
                // 设置会话管理策略为无状态（STATELESS），不创建HTTP Session
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // 配置HTTP请求的授权规则
                .authorizeHttpRequests(authorize -> authorize
                        // 公开访问：登录页面及静态资源
                        .requestMatchers("/login.html", "/static/**", "/*.css", "/*.js", "/favicon.ico").permitAll()
                        // 仅限ADMIN角色访问：管理页面
                        .requestMatchers("/admin.html").hasRole("ADMIN")
                        // USER或ADMIN角色均可访问：聊天页面
                        .requestMatchers("/chat.html").hasAnyRole("USER", "ADMIN")
                        // 仅限ADMIN角色访问：文档管理API
                        .requestMatchers("/api/documents/**").hasRole("ADMIN")
                        // USER或ADMIN角色均可访问：聊天API
                        .requestMatchers("/api/chat/**").hasAnyRole("USER", "ADMIN")
                        // 其他所有请求都需要经过认证
                        .anyRequest().authenticated())
                // 启用HTTP Basic认证作为认证方式
                .httpBasic(Customizer.withDefaults());

        return http.build();
    }

    /**
     * 配置用户详情服务，提供用户信息用于认证。
     * <p>
     * 此处使用内存存储，定义了两个用户：'user' 和 'admin'。
     *
     * @return UserDetailsService 实例
     */
    @Bean
    public UserDetailsService userDetailsService() {
        // 定义普通用户 'user'
        UserDetails user = User.withDefaultPasswordEncoder()
                .username("user")
                .password("password")
                .roles("USER")
                .build();

        // 定义管理员用户 'admin'
        UserDetails admin = User.withDefaultPasswordEncoder()
                .username("admin")
                .password("admin")
                .roles("ADMIN")
                .build();

        // 使用内存用户详情管理器
        return new InMemoryUserDetailsManager(user, admin);
    }

    /**
     * 配置CORS（跨域资源共享）策略。
     * <p>
     * 允许所有来源、所有HTTP方法和所有请求头，方便前后端分离开发。
     *
     * @return CorsConfigurationSource 实例
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // 允许来自任何源的请求
        configuration.setAllowedOrigins(Arrays.asList("*"));
        // 允许所有标准的HTTP方法
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        // 允许所有请求头
        configuration.setAllowedHeaders(Arrays.asList("*"));
        // 禁用凭证（如Cookies），因为是无状态API
        configuration.setAllowCredentials(false);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        // 将此CORS配置应用到所有路径
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
