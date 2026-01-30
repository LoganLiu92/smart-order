package com.smartorder.service;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JwtUtil {
  private final SecretKey key;
  private final long accessTtlMinutes;

  public JwtUtil(@Value("${app.auth.jwt-secret}") String secret,
                @Value("${app.auth.access-ttl-minutes:60}") long accessTtlMinutes) {
    this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    this.accessTtlMinutes = accessTtlMinutes;
  }

  public String generateAccessToken(String storeId, String username, String role) {
    Instant now = Instant.now();
    Instant exp = now.plusSeconds(accessTtlMinutes * 60);
    return Jwts.builder()
        .subject(username)
        .claim("storeId", storeId)
        .claim("role", role)
        .issuedAt(Date.from(now))
        .expiration(Date.from(exp))
        .signWith(key, SignatureAlgorithm.HS256)
        .compact();
  }

  public JwtClaims parse(String token) {
    var claims = Jwts.parser()
        .verifyWith(key)
        .build()
        .parseSignedClaims(token)
        .getPayload();
    JwtClaims result = new JwtClaims();
    result.username = claims.getSubject();
    result.storeId = claims.get("storeId", String.class);
    result.role = claims.get("role", String.class);
    result.expiresAt = claims.getExpiration().toInstant();
    return result;
  }

  public static class JwtClaims {
    public String storeId;
    public String username;
    public String role;
    public Instant expiresAt;
  }
}
