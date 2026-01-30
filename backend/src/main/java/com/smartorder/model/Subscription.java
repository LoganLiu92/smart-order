package com.smartorder.model;

import java.time.Instant;

public class Subscription {
  public String storeId;
  public String status; // ACTIVE, PAUSED
  public Instant expireAt;
}
