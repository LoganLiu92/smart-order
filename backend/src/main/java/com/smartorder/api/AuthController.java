package com.smartorder.api;

import com.smartorder.api.dto.AuthLoginRequest;
import com.smartorder.api.dto.AuthLoginResponse;
import com.smartorder.api.dto.AuthRegisterRequest;
import com.smartorder.model.User;
import com.smartorder.model.StoreInfo;
import com.smartorder.service.AuthService;
import com.smartorder.service.BillingService;
import com.smartorder.service.MenuService;
import com.smartorder.service.StoreService;
import com.smartorder.service.UserService;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
  private final UserService userService;
  private final BillingService billingService;
  private final AuthService authService;
  private final StoreService storeService;
  private final MenuService menuService;

  public AuthController(UserService userService, BillingService billingService, AuthService authService,
                        StoreService storeService, MenuService menuService) {
    this.userService = userService;
    this.billingService = billingService;
    this.authService = authService;
    this.storeService = storeService;
    this.menuService = menuService;
  }

  @PostMapping("/login")
  public AuthLoginResponse login(@RequestBody AuthLoginRequest request) {
    if (request.storeId == null || request.username == null || request.password == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing credentials");
    }
    User user = userService.validate(request.storeId, request.username, request.password);
    if (user == null) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
    }
    billingService.getWallet(user.storeId);
    billingService.getSubscription(user.storeId);

    AuthService.Tokens tokens = authService.issueTokens(user.storeId, user.username, user.role);
    AuthLoginResponse response = new AuthLoginResponse();
    response.accessToken = tokens.accessToken;
    response.refreshToken = tokens.refreshToken;
    response.expiresIn = tokens.expiresInSeconds;
    response.role = user.role;
    response.storeId = user.storeId;
    response.username = user.username;
    return response;
  }

  @PostMapping("/register")
  public AuthLoginResponse register(@RequestBody AuthRegisterRequest request) {
    if (request.storeId == null || request.username == null || request.password == null || request.storeName == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing credentials");
    }
    StoreInfo storeInfo = new StoreInfo();
    storeInfo.id = request.storeId;
    storeInfo.name = request.storeName;
    storeService.update(request.storeId, storeInfo);
    menuService.createEmptyMenu(request.storeId);
    User user = userService.createUser(request.storeId, request.username, request.password, "ADMIN");
    billingService.getWallet(user.storeId);
    billingService.createTrialSubscription(user.storeId);

    AuthService.Tokens tokens = authService.issueTokens(user.storeId, user.username, user.role);
    AuthLoginResponse response = new AuthLoginResponse();
    response.accessToken = tokens.accessToken;
    response.refreshToken = tokens.refreshToken;
    response.expiresIn = tokens.expiresInSeconds;
    response.role = user.role;
    response.storeId = user.storeId;
    response.username = user.username;
    return response;
  }

  @PostMapping("/refresh")
  public AuthLoginResponse refresh(@RequestBody Map<String, String> request) {
    String refreshToken = request.get("refreshToken");
    if (refreshToken == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing refresh token");
    }
    AuthService.Tokens tokens = authService.refresh(refreshToken);
    if (tokens == null) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token");
    }
    AuthLoginResponse response = new AuthLoginResponse();
    response.accessToken = tokens.accessToken;
    response.refreshToken = tokens.refreshToken;
    response.expiresIn = tokens.expiresInSeconds;
    return response;
  }
}
