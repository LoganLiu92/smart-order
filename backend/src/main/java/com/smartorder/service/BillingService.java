package com.smartorder.service;

import com.smartorder.model.LedgerEntry;
import com.smartorder.model.Pricing;
import com.smartorder.model.Subscription;
import com.smartorder.model.Wallet;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class BillingService {
  private final Map<String, Wallet> wallets = new ConcurrentHashMap<>();
  private final Map<String, Subscription> subscriptions = new ConcurrentHashMap<>();
  private final Pricing pricing = new Pricing();
  private final Map<String, Long> aiCallCounts = new ConcurrentHashMap<>();
  private final Map<String, Long> aiTokenCounts = new ConcurrentHashMap<>();

  public BillingService() {
    pricing.platformMonthlyFee = 199.0;
    pricing.storeMonthlyFee = 99.0;
    pricing.aiCallPrice = 0.01;
  }

  public Wallet getWallet(String storeId) {
    return wallets.computeIfAbsent(storeId, id -> {
      Wallet wallet = new Wallet();
      wallet.storeId = id;
      wallet.balance = 0;
      return wallet;
    });
  }

  public Subscription getSubscription(String storeId) {
    return subscriptions.computeIfAbsent(storeId, id -> {
      Subscription sub = new Subscription();
      sub.storeId = id;
      sub.status = "ACTIVE";
      sub.expireAt = Instant.now().plus(30, ChronoUnit.DAYS);
      return sub;
    });
  }

  public void createTrialSubscription(String storeId) {
    Subscription sub = new Subscription();
    sub.storeId = storeId;
    sub.status = "ACTIVE";
    sub.expireAt = Instant.now().plus(3, ChronoUnit.DAYS);
    subscriptions.put(storeId, sub);
  }

  public boolean isSubscriptionActive(String storeId) {
    Subscription sub = subscriptions.get(storeId);
    if (sub == null) return false;
    if (!"ACTIVE".equals(sub.status)) return false;
    return sub.expireAt != null && sub.expireAt.isAfter(Instant.now());
  }

  public Pricing getPricing() {
    return pricing;
  }

  public Pricing updatePricing(Pricing next) {
    pricing.platformMonthlyFee = next.platformMonthlyFee;
    pricing.storeMonthlyFee = next.storeMonthlyFee;
    pricing.aiCallPrice = next.aiCallPrice;
    return pricing;
  }

  public LedgerEntry topup(String storeId, double amount, String reason) {
    Wallet wallet = getWallet(storeId);
    wallet.balance += amount;
    LedgerEntry entry = createEntry(storeId, "TOPUP", reason, amount);
    wallet.ledger.add(0, entry);
    return entry;
  }

  public LedgerEntry charge(String storeId, String type, String reason, double amount) {
    Wallet wallet = getWallet(storeId);
    wallet.balance -= amount;
    LedgerEntry entry = createEntry(storeId, type, reason, -amount);
    wallet.ledger.add(0, entry);
    return entry;
  }

  public void pauseSubscription(String storeId) {
    Subscription sub = getSubscription(storeId);
    sub.status = "PAUSED";
  }

  public void renewSubscription(String storeId, int days) {
    Subscription sub = getSubscription(storeId);
    sub.status = "ACTIVE";
    sub.expireAt = sub.expireAt.isAfter(Instant.now()) ? sub.expireAt.plus(days, ChronoUnit.DAYS)
        : Instant.now().plus(days, ChronoUnit.DAYS);
  }

  public void chargeStoreSubscription(String storeId) {
    Wallet wallet = getWallet(storeId);
    if (wallet.balance < pricing.storeMonthlyFee) {
      return;
    }
    charge(storeId, "SUBSCRIPTION", "Monthly store subscription", pricing.storeMonthlyFee);
    renewSubscription(storeId, 30);
  }

  public void recordAiCall(String storeId, long tokens) {
    aiCallCounts.put(storeId, aiCallCounts.getOrDefault(storeId, 0L) + 1);
    aiTokenCounts.put(storeId, aiTokenCounts.getOrDefault(storeId, 0L) + tokens);
    charge(storeId, "AI_CALL", "AI usage", pricing.aiCallPrice);
  }

  public long getAiCalls(String storeId) {
    return aiCallCounts.getOrDefault(storeId, 0L);
  }

  public long getAiTokens(String storeId) {
    return aiTokenCounts.getOrDefault(storeId, 0L);
  }

  public List<Map<String, Object>> listStoreSummaries() {
    List<Map<String, Object>> list = new ArrayList<>();
    for (String storeId : wallets.keySet()) {
      Wallet wallet = getWallet(storeId);
      Subscription sub = getSubscription(storeId);
      list.add(Map.of(
          "storeId", storeId,
          "balance", wallet.balance,
          "subscriptionStatus", sub.status,
          "subscriptionExpireAt", sub.expireAt,
          "aiCalls", getAiCalls(storeId),
          "aiTokens", getAiTokens(storeId)
      ));
    }
    return list;
  }

  @Scheduled(cron = "0 0 0 * * *")
  public void autoRenewSubscriptions() {
    Instant now = Instant.now();
    for (String storeId : wallets.keySet()) {
      Subscription sub = getSubscription(storeId);
      if (sub.expireAt == null) continue;
      long daysLeft = ChronoUnit.DAYS.between(now, sub.expireAt);
      if (daysLeft <= 7 && daysLeft >= 0) {
        chargeStoreSubscription(storeId);
      }
    }
  }

  private LedgerEntry createEntry(String storeId, String type, String reason, double amount) {
    LedgerEntry entry = new LedgerEntry();
    entry.id = UUID.randomUUID().toString();
    entry.storeId = storeId;
    entry.type = type;
    entry.reason = reason;
    entry.amount = amount;
    entry.createdAt = Instant.now();
    return entry;
  }
}
