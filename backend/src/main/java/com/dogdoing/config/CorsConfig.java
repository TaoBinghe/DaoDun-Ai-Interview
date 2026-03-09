package com.dogdoing.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * CORS 过滤器，最高优先级运行，确保预检请求（OPTIONS）在任何安全校验之前得到正确处理
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorsConfig extends OncePerRequestFilter {

    private final CorsConfigurationSource corsConfigurationSource = corsConfigurationSource();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        CorsConfiguration config = corsConfigurationSource.getCorsConfiguration(request);
        if (config != null) {
            String origin = request.getHeader("Origin");
            if (origin != null && config.getAllowedOriginPatterns().stream()
                    .anyMatch(pattern -> matchesOrigin(pattern, origin))) {
                response.setHeader("Access-Control-Allow-Origin", origin);
            }
            response.setHeader("Access-Control-Allow-Methods", String.join(", ", config.getAllowedMethods()));
            response.setHeader("Access-Control-Allow-Headers", String.join(", ", config.getAllowedHeaders()));
            response.setHeader("Access-Control-Allow-Credentials", String.valueOf(config.getAllowCredentials()));
            response.setHeader("Access-Control-Max-Age", String.valueOf(config.getMaxAge()));
        }

        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            response.setStatus(HttpServletResponse.SC_OK);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean matchesOrigin(String pattern, String origin) {
        if ("*".equals(pattern)) return true;
        if (pattern.contains("*")) {
            String regex = pattern.replace(".", "\\.").replace("*", ".*");
            return origin.matches(regex);
        }
        return pattern.equals(origin);
    }

    private CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of(
                "http://localhost:5000",
                "http://127.0.0.1:5000",
                "http://localhost:5173",
                "http://localhost:5174",
                "http://127.0.0.1:5173",
                "http://127.0.0.1:5174"
        ));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
