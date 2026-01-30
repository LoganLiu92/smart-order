package com.smartorder.service;

import java.time.Instant;

public class AuthSession {
  public String id;
  public String storeId;
  public String username;
  public String role;
  public String refreshToken;
  public Instant refreshExpiresAt;
}
