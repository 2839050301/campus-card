package com.campus.card.util;

import com.campus.card.vo.LoginUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;

import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * @Description
 * @Author u
 * @Date 2026/9/30
 */
@Slf4j
@Component
public class JwtUtil {
    private final SecretKey key;
    private final Long ttlMillis;

    public JwtUtil(@Value("${campus.jwt.secret}") String secret,
                   @Value("${campus.jwt.ttl-minutes}") Long ttlMillis) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.ttlMillis = ttlMillis * 60_000L;
    }

    //签发token：把登录用户的信息整个塞进 claims
    public String create(LoginUser u){
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(u.getUserId()))
                .claim("account",u.getAccount())
                .claim("name",u.getName())
                .claim("role",u.getRole())
                .claim("cardNo",u.getCardNo())
                .claim("college",u.getCollege())
                .issuedAt(now)
                .expiration(new Date(now.getTime()+ttlMillis))
                .signWith(key)
                .compact();
    }
    //解析token
    public LoginUser parse(String token){
        try {
            Claims c = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            LoginUser u = new LoginUser();
            u.setUserId(Long.valueOf(c.getSubject()));
            u.setAccount(c.get("account", String.class));
            u.setName(c.get("name", String.class));
            u.setRole(c.get("role", String.class));
            u.setCardNo(c.get("cardNo", String.class));
            u.setCollege(c.get("college", String.class));
            return u;
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("token校验失败:{}",e.getMessage());
           return null;
        }
    }



}
