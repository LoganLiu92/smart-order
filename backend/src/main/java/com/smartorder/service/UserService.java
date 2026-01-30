package com.smartorder.service;

import com.smartorder.model.User;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class UserService {
  private final Map<String, User> users = new ConcurrentHashMap<>();

  public UserService() {
  }

  private String key(String storeId, String username) {
    return storeId + ":" + username;
  }

  public User createUser(String storeId, String username, String password, String role) {
    User user = new User();
    user.id = UUID.randomUUID().toString();
    user.storeId = storeId;
    user.username = username;
    user.password = password;
    user.role = role;
    users.put(key(storeId, username), user);
    return user;
  }

  public User validate(String storeId, String username, String password) {
    User user = users.get(key(storeId, username));
    if (user == null) return null;
    if (!user.password.equals(password)) return null;
    return user;
  }

  public List<User> listByStore(String storeId) {
    List<User> result = new ArrayList<>();
    for (User user : users.values()) {
      if (storeId.equals(user.storeId)) {
        result.add(user);
      }
    }
    return result;
  }
}
