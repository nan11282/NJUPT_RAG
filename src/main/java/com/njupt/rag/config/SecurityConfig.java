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

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // 启用CORS
                .cors(Customizer.withDefaults())
                // 因为使用token，所以禁用csrf
                .csrf(csrf -> csrf.disable())
                // 配置会话管理为无状态，不通过session获取SecurityContext
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        // 允许访问登录页面和静态资源
                        .requestMatchers("/login.html", "/static/**", "/*.css", "/*.js", "/favicon.ico").permitAll()
                        // admin.html 仅对ADMIN开放
                        .requestMatchers("/admin.html").hasRole("ADMIN")
                        // chat.html 对USER和ADMIN开放
                        .requestMatchers("/chat.html").hasAnyRole("USER", "ADMIN")
                        // 文档上传接口仅对ADMIN开放
                        .requestMatchers("/api/documents/**").hasRole("ADMIN")
                        // 聊天接口对USER和ADMIN开放
                        .requestMatchers("/api/chat/**").hasAnyRole("USER", "ADMIN")
                        // 其他所有请求都需要认证
                        .anyRequest().authenticated())
                // 启用HTTP Basic认证
                .httpBasic(Customizer.withDefaults());

        return http.build();
    }

    @Bean
    public UserDetailsService userDetailsService() {
        // 创建一个普通用户
        UserDetails user = User.withDefaultPasswordEncoder()
                .username("user")
                .password("password")
                .roles("USER")
                .build();

        // 创建一个管理员用户
        UserDetails admin = User.withDefaultPasswordEncoder()
                .username("admin")
                .password("admin")
                .roles("ADMIN")
                .build();

        // 使用InMemoryUserDetailsManager管理用户
        return new InMemoryUserDetailsManager(user, admin);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // 允许所有来源
        configuration.setAllowedOrigins(Arrays.asList("*"));
        // 允许所有HTTP方法
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        // 允许所有请求头
        configuration.setAllowedHeaders(Arrays.asList("*"));
        // 允许携带凭证
        configuration.setAllowCredentials(false);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        // 对所有URL应用该CORS配置
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
