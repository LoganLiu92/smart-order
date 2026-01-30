package com.smartorder.model;

import java.time.Instant;

public class LedgerEntry {
  public String id;
  public String storeId;
  public String type;
  public String reason;
  public double amount;
  public Instant createdAt;
}
