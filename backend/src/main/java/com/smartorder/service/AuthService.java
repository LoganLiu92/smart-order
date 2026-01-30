package com.smartorder.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
  public static class Tokens {
    public String accessToken;
    public String refreshToken;
    public long expiresInSeconds;
  }

  private final AuthSessionStore repository;
  private final JwtUtil jwtUtil;
  private final long refreshTtlDays;
  private final long accessTtlMinutes;

  public AuthService(AuthSessionStore repository, JwtUtil jwtUtil,
                     @Value("${app.auth.refresh-ttl-days:30}") long refreshTtlDays,
                     @Value("${app.auth.access-ttl-minutes:60}") long accessTtlMinutes) {
    this.repository = repository;
    this.jwtUtil = jwtUtil;
    this.refreshTtlDays = refreshTtlDays;
    this.accessTtlMinutes = accessTtlMinutes;
  }

  public Tokens issueTokens(String storeId, String username, String role) {
    String accessToken = jwtUtil.generateAccessToken(storeId, username, role);
    String refreshToken = UUID.randomUUID().toString();
    Instant refreshExpiresAt = Instant.now().plus(refreshTtlDays, ChronoUnit.DAYS);
    repository.create(storeId, username, role, refreshToken, refreshExpiresAt);

    Tokens tokens = new Tokens();
    tokens.accessToken = accessToken;
    tokens.refreshToken = refreshToken;
    tokens.expiresInSeconds = accessTtlMinutes * 60;
    return tokens;
  }

  public Tokens refresh(String refreshToken) {
    AuthSession session = repository.findByRefreshToken(refreshToken);
    if (session == null || session.refreshExpiresAt.isBefore(Instant.now())) {
      return null;
    }
    String newAccess = jwtUtil.generateAccessToken(session.storeId, session.username, session.role);
    String newRefresh = UUID.randomUUID().toString();
    Instant refreshExpiresAt = Instant.now().plus(refreshTtlDays, ChronoUnit.DAYS);
    repository.replaceRefreshToken(session.id, newRefresh, refreshExpiresAt);

    Tokens tokens = new Tokens();
    tokens.accessToken = newAccess;
    tokens.refreshToken = newRefresh;
    tokens.expiresInSeconds = accessTtlMinutes * 60;
    return tokens;
  }
}
