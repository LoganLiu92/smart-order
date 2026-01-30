package com.smartorder.api.dto;

public class AuthLoginResponse {
  public String accessToken;
  public String refreshToken;
  public long expiresIn;
  public String role;
  public String storeId;
  public String username;
}
