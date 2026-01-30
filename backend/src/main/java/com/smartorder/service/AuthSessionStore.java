package com.smartorder.service;

public interface AuthSessionStore {
  AuthSession create(String storeId, String username, String role, String refreshToken, java.time.Instant refreshExpiresAt);
  AuthSession findByRefreshToken(String refreshToken);
  void replaceRefreshToken(String id, String newToken, java.time.Instant newExpiresAt);
}
