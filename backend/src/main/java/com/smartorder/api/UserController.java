package com.smartorder.api;

import com.smartorder.model.User;
import com.smartorder.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {
  private final UserService userService;

  public UserController(UserService userService) {
    this.userService = userService;
  }

  @GetMapping
  public List<User> list(@RequestParam String storeId) {
    return userService.listByStore(storeId);
  }

  @PostMapping
  public User create(@RequestBody Map<String, String> request, HttpServletRequest http) {
    String storeId = request.get("storeId");
    Object authStore = http.getAttribute("auth.storeId");
    if (authStore instanceof String authStoreId && storeId != null && !storeId.equals(authStoreId)) {
      throw new org.springframework.web.server.ResponseStatusException(
          org.springframework.http.HttpStatus.FORBIDDEN, "Forbidden");
    }
    String username = request.get("username");
    String password = request.get("password");
    String role = request.getOrDefault("role", "CASHIER");
    return userService.createUser(storeId, username, password, role);
  }
}
