package com.smartorder.model;

import java.util.ArrayList;
import java.util.List;

public class Wallet {
  public String storeId;
  public double balance;
  public List<LedgerEntry> ledger = new ArrayList<>();
}
