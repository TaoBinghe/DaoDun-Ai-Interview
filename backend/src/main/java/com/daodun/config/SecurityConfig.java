package com.daodun.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.util.Map;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ObjectMapper objectMapper;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // 禁用 CSRF（前后端分离使用 JWT，无需 CSRF）
            .csrf(AbstractHttpConfigurer::disable)
            // CORS 由 CorsConfig 过滤器（最高优先级）统一处理
            // 无状态 Session（JWT 不需要 Session）
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // 请求权限配置
            .authorizeHttpRequests(auth -> auth
                // 认证相关接口全部放行
                .requestMatchers("/api/auth/**").permitAll()
                // WebSocket 握手由自定义握手拦截器校验 token
                .requestMatchers("/ws/**").permitAll()
                // 语音 TTS 诊断接口（无需登录，便于排查配置）
                .requestMatchers("/api/voice/diagnostics/**").permitAll()
                // 其他所有接口需要认证
                .anyRequest().authenticated()
            )
            // 未认证时返回 JSON 而非重定向到登录页
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authException) -> {
                    response.setStatus(HttpStatus.UNAUTHORIZED.value());
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8");
                    response.getWriter().write(
                        objectMapper.writeValueAsString(
                            Map.of("code", 401, "msg", "未登录或 Token 已过期，请重新登录")
                        )
                    );
                })
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    response.setStatus(HttpStatus.FORBIDDEN.value());
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8");
                    response.getWriter().write(
                        objectMapper.writeValueAsString(
                            Map.of("code", 403, "msg", "权限不足")
                        )
                    );
                })
            )
            // 在 UsernamePasswordAuthenticationFilter 之前执行 JWT 过滤器
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
