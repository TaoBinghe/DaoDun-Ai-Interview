package com.dogdoing.util;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Slf4j
@Component
public class JwtUtil {

    private static final String CLAIM_USER_ID = "userId";
    private static final String CLAIM_TOKEN_TYPE = "type";
    private static final String TYPE_ACCESS = "access";
    private static final String TYPE_REFRESH = "refresh";

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.access-token-expiration}")
    private long accessTokenExpiration;

    @Value("${jwt.refresh-token-expiration}")
    private long refreshTokenExpiration;

    @Value("${jwt.refresh-token-remember-me-expiration}")
    private long refreshTokenRememberMeExpiration;

    private SecretKey getSigningKey() {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * 生成 Access Token（2小时有效）
     */
    public String generateAccessToken(Long userId) {
        return buildToken(userId, TYPE_ACCESS, accessTokenExpiration * 1000L);
    }

    /**
     * 生成 Refresh Token
     *
     * @param rememberMe true 时有效期 14 天，否则 7 天
     */
    public String generateRefreshToken(Long userId, boolean rememberMe) {
        long expMillis = rememberMe
                ? refreshTokenRememberMeExpiration * 1000L
                : refreshTokenExpiration * 1000L;
        return buildToken(userId, TYPE_REFRESH, expMillis);
    }

    private String buildToken(Long userId, String type, long expirationMillis) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMillis);

        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(CLAIM_USER_ID, userId)
                .claim(CLAIM_TOKEN_TYPE, type)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * 从 Token 中解析用户 ID
     *
     * @return 用户 ID，解析失败返回 null
     */
    public Long getUserIdFromToken(String token) {
        try {
            Claims claims = parseClaims(token);
            return claims.get(CLAIM_USER_ID, Long.class);
        } catch (Exception e) {
            log.warn("解析 JWT 用户 ID 失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 验证 Token 是否是有效的 Access Token
     */
    public boolean isValidAccessToken(String token) {
        return isValidToken(token, TYPE_ACCESS);
    }

    /**
     * 验证 Token 是否是有效的 Refresh Token
     */
    public boolean isValidRefreshToken(String token) {
        return isValidToken(token, TYPE_REFRESH);
    }

    private boolean isValidToken(String token, String expectedType) {
        try {
            Claims claims = parseClaims(token);
            String tokenType = claims.get(CLAIM_TOKEN_TYPE, String.class);
            return expectedType.equals(tokenType);
        } catch (ExpiredJwtException e) {
            log.warn("JWT 已过期: {}", e.getMessage());
            return false;
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("JWT 验证失败: {}", e.getMessage());
            return false;
        }
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
