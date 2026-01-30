package com.smartorder.service;

import com.smartorder.model.User;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class UserService {
  private final JdbcTemplate jdbcTemplate;

  public UserService(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public User createUser(String storeId, String username, String password, String role) {
    User user = new User();
    user.id = UUID.randomUUID().toString();
    user.storeId = storeId;
    user.username = username;
    user.password = password;
    user.role = role;
    jdbcTemplate.update(
        "INSERT INTO users (id, store_id, username, password, role) VALUES (?, ?, ?, ?, ?)",
        user.id, user.storeId, user.username, user.password, user.role);
    return user;
  }

  public User validate(String storeId, String username, String password) {
    List<User> list = jdbcTemplate.query(
        "SELECT * FROM users WHERE store_id=? AND username=? AND password=?",
        new Object[] { storeId, username, password },
        (rs, rowNum) -> {
          User user = new User();
          user.id = rs.getString("id");
          user.storeId = rs.getString("store_id");
          user.username = rs.getString("username");
          user.password = rs.getString("password");
          user.role = rs.getString("role");
          return user;
        });
    return list.isEmpty() ? null : list.get(0);
  }

  public List<User> listByStore(String storeId) {
    return jdbcTemplate.query(
        "SELECT * FROM users WHERE store_id=? ORDER BY username",
        new Object[] { storeId },
        (rs, rowNum) -> {
          User user = new User();
          user.id = rs.getString("id");
          user.storeId = rs.getString("store_id");
          user.username = rs.getString("username");
          user.password = rs.getString("password");
          user.role = rs.getString("role");
          return user;
        });
  }
}
