package com.smartorder.service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AuthSessionRepository {
  private final JdbcTemplate jdbcTemplate;

  public AuthSessionRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public AuthSession create(String storeId, String username, String role, String refreshToken, Instant refreshExpiresAt) {
    String id = UUID.randomUUID().toString();
    jdbcTemplate.update(
        "INSERT INTO auth_sessions (id, store_id, username, role, refresh_token, refresh_expires_at) VALUES (?, ?, ?, ?, ?, ?)",
        id, storeId, username, role, refreshToken, refreshExpiresAt);
    AuthSession session = new AuthSession();
    session.id = id;
    session.storeId = storeId;
    session.username = username;
    session.role = role;
    session.refreshToken = refreshToken;
    session.refreshExpiresAt = refreshExpiresAt;
    return session;
  }

  public AuthSession findByRefreshToken(String refreshToken) {
    return jdbcTemplate.query(
        "SELECT id, store_id, username, role, refresh_token, refresh_expires_at FROM auth_sessions WHERE refresh_token = ?",
        new Object[] { refreshToken },
        rs -> rs.next() ? map(rs) : null
    );
  }

  public void replaceRefreshToken(String id, String newToken, Instant newExpiresAt) {
    jdbcTemplate.update(
        "UPDATE auth_sessions SET refresh_token = ?, refresh_expires_at = ? WHERE id = ?",
        newToken, newExpiresAt, id);
  }

  private AuthSession map(ResultSet rs) throws SQLException {
    AuthSession session = new AuthSession();
    session.id = rs.getString("id");
    session.storeId = rs.getString("store_id");
    session.username = rs.getString("username");
    session.role = rs.getString("role");
    session.refreshToken = rs.getString("refresh_token");
    session.refreshExpiresAt = rs.getTimestamp("refresh_expires_at").toInstant();
    return session;
  }
}
