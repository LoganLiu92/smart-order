package com.smartorder.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RedisAuthSessionStore implements AuthSessionStore {
  private final StringRedisTemplate redisTemplate;
  private final ObjectMapper objectMapper;

  public RedisAuthSessionStore(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
    this.redisTemplate = redisTemplate;
    this.objectMapper = objectMapper;
  }

  private String sessionKey(String id) {
    return "auth:session:" + id;
  }

  private String refreshKey(String token) {
    return "auth:refresh:" + token;
  }

  @Override
  public AuthSession create(String storeId, String username, String role, String refreshToken, Instant refreshExpiresAt) {
    AuthSession session = new AuthSession();
    session.id = UUID.randomUUID().toString();
    session.storeId = storeId;
    session.username = username;
    session.role = role;
    session.refreshToken = refreshToken;
    session.refreshExpiresAt = refreshExpiresAt;
    save(session);
    return session;
  }

  @Override
  public AuthSession findByRefreshToken(String refreshToken) {
    String id = redisTemplate.opsForValue().get(refreshKey(refreshToken));
    if (id == null) return null;
    String json = redisTemplate.opsForValue().get(sessionKey(id));
    if (json == null) return null;
    try {
      AuthSession session = objectMapper.readValue(json, AuthSession.class);
      if (!refreshToken.equals(session.refreshToken)) {
        return null;
      }
      return session;
    } catch (Exception ex) {
      return null;
    }
  }

  @Override
  public void replaceRefreshToken(String id, String newToken, Instant newExpiresAt) {
    String json = redisTemplate.opsForValue().get(sessionKey(id));
    if (json == null) return;
    try {
      AuthSession session = objectMapper.readValue(json, AuthSession.class);
      String oldToken = session.refreshToken;
      session.refreshToken = newToken;
      session.refreshExpiresAt = newExpiresAt;
      save(session);
      if (oldToken != null) {
        redisTemplate.delete(refreshKey(oldToken));
      }
    } catch (Exception ex) {
      // ignore
    }
  }

  private void save(AuthSession session) {
    try {
      String json = objectMapper.writeValueAsString(session);
      Duration ttl = Duration.between(Instant.now(), session.refreshExpiresAt);
      if (ttl.isNegative()) ttl = Duration.ofSeconds(1);
      redisTemplate.opsForValue().set(sessionKey(session.id), json, ttl);
      redisTemplate.opsForValue().set(refreshKey(session.refreshToken), session.id, ttl);
    } catch (Exception ex) {
      // ignore
    }
  }
}
