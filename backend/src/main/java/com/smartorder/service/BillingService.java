package com.smartorder.service;

import com.smartorder.model.LedgerEntry;
import com.smartorder.model.Pricing;
import com.smartorder.model.Subscription;
import com.smartorder.model.Wallet;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BillingService {
  private final JdbcTemplate jdbcTemplate;

  public BillingService(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public Wallet getWallet(String storeId) {
    List<Wallet> list = jdbcTemplate.query(
        "SELECT store_id, balance FROM wallets WHERE store_id=?",
        new Object[] { storeId },
        (rs, rowNum) -> {
          Wallet wallet = new Wallet();
          wallet.storeId = rs.getString("store_id");
          wallet.balance = rs.getDouble("balance");
          return wallet;
        });
    Wallet wallet;
    if (list.isEmpty()) {
      jdbcTemplate.update("INSERT INTO wallets (store_id, balance) VALUES (?, ?)", storeId, 0);
      wallet = new Wallet();
      wallet.storeId = storeId;
      wallet.balance = 0;
    } else {
      wallet = list.get(0);
    }

    wallet.ledger = jdbcTemplate.query(
        "SELECT * FROM wallet_ledger WHERE store_id=? ORDER BY created_at DESC",
        new Object[] { storeId },
        (rs, rowNum) -> {
          LedgerEntry entry = new LedgerEntry();
          entry.id = rs.getString("id");
          entry.storeId = rs.getString("store_id");
          entry.type = rs.getString("type");
          entry.reason = rs.getString("reason");
          entry.amount = rs.getDouble("amount");
          entry.createdAt = rs.getTimestamp("created_at").toInstant();
          return entry;
        });

    return wallet;
  }

  public Subscription getSubscription(String storeId) {
    List<Subscription> list = jdbcTemplate.query(
        "SELECT store_id, status, expire_at FROM subscriptions WHERE store_id=?",
        new Object[] { storeId },
        (rs, rowNum) -> {
          Subscription sub = new Subscription();
          sub.storeId = rs.getString("store_id");
          sub.status = rs.getString("status");
          Timestamp expireAt = rs.getTimestamp("expire_at");
          sub.expireAt = expireAt == null ? null : expireAt.toInstant();
          return sub;
        });
    if (!list.isEmpty()) {
      return list.get(0);
    }
    Subscription sub = new Subscription();
    sub.storeId = storeId;
    sub.status = "ACTIVE";
    sub.expireAt = Instant.now().plus(30, ChronoUnit.DAYS);
    jdbcTemplate.update(
        "INSERT INTO subscriptions (store_id, status, expire_at) VALUES (?, ?, ?)",
        sub.storeId,
        sub.status,
        Timestamp.from(sub.expireAt));
    return sub;
  }

  public void createTrialSubscription(String storeId) {
    Subscription sub = new Subscription();
    sub.storeId = storeId;
    sub.status = "ACTIVE";
    sub.expireAt = Instant.now().plus(3, ChronoUnit.DAYS);
    jdbcTemplate.update("DELETE FROM subscriptions WHERE store_id=?", storeId);
    jdbcTemplate.update(
        "INSERT INTO subscriptions (store_id, status, expire_at) VALUES (?, ?, ?)",
        sub.storeId,
        sub.status,
        Timestamp.from(sub.expireAt));
  }

  public boolean isSubscriptionActive(String storeId) {
    Subscription sub = getSubscription(storeId);
    if (sub == null) return false;
    if (!"ACTIVE".equals(sub.status)) return false;
    return sub.expireAt != null && sub.expireAt.isAfter(Instant.now());
  }

  public Pricing getPricing() {
    List<Pricing> list = jdbcTemplate.query(
        "SELECT * FROM platform_pricing WHERE id='default'",
        (rs, rowNum) -> {
          Pricing pricing = new Pricing();
          pricing.platformMonthlyFee = rs.getDouble("platform_monthly_fee");
          pricing.storeMonthlyFee = rs.getDouble("store_monthly_fee");
          pricing.aiCallPrice = rs.getDouble("ai_call_price");
          return pricing;
        });
    if (!list.isEmpty()) return list.get(0);

    Pricing pricing = new Pricing();
    pricing.platformMonthlyFee = 199.0;
    pricing.storeMonthlyFee = 99.0;
    pricing.aiCallPrice = 0.01;
    jdbcTemplate.update(
        "INSERT INTO platform_pricing (id, platform_monthly_fee, store_monthly_fee, ai_call_price) VALUES ('default', ?, ?, ?)",
        pricing.platformMonthlyFee,
        pricing.storeMonthlyFee,
        pricing.aiCallPrice);
    return pricing;
  }

  public Pricing updatePricing(Pricing next) {
    Pricing pricing = getPricing();
    pricing.platformMonthlyFee = next.platformMonthlyFee;
    pricing.storeMonthlyFee = next.storeMonthlyFee;
    pricing.aiCallPrice = next.aiCallPrice;
    jdbcTemplate.update(
        "UPDATE platform_pricing SET platform_monthly_fee=?, store_monthly_fee=?, ai_call_price=? WHERE id='default'",
        pricing.platformMonthlyFee,
        pricing.storeMonthlyFee,
        pricing.aiCallPrice);
    return pricing;
  }

  @Transactional
  public LedgerEntry topup(String storeId, double amount, String reason) {
    getWallet(storeId);
    jdbcTemplate.update("UPDATE wallets SET balance=balance+? WHERE store_id=?", amount, storeId);
    LedgerEntry entry = createEntry(storeId, "TOPUP", reason, amount);
    jdbcTemplate.update(
        "INSERT INTO wallet_ledger (id, store_id, type, reason, amount, created_at) VALUES (?, ?, ?, ?, ?, ?)",
        entry.id,
        entry.storeId,
        entry.type,
        entry.reason,
        entry.amount,
        Timestamp.from(entry.createdAt));
    return entry;
  }

  @Transactional
  public LedgerEntry charge(String storeId, String type, String reason, double amount) {
    getWallet(storeId);
    jdbcTemplate.update("UPDATE wallets SET balance=balance-? WHERE store_id=?", amount, storeId);
    LedgerEntry entry = createEntry(storeId, type, reason, -amount);
    jdbcTemplate.update(
        "INSERT INTO wallet_ledger (id, store_id, type, reason, amount, created_at) VALUES (?, ?, ?, ?, ?, ?)",
        entry.id,
        entry.storeId,
        entry.type,
        entry.reason,
        entry.amount,
        Timestamp.from(entry.createdAt));
    return entry;
  }

  public void pauseSubscription(String storeId) {
    jdbcTemplate.update("UPDATE subscriptions SET status=? WHERE store_id=?", "PAUSED", storeId);
  }

  public void renewSubscription(String storeId, int days) {
    Subscription sub = getSubscription(storeId);
    sub.status = "ACTIVE";
    sub.expireAt = sub.expireAt != null && sub.expireAt.isAfter(Instant.now())
        ? sub.expireAt.plus(days, ChronoUnit.DAYS)
        : Instant.now().plus(days, ChronoUnit.DAYS);
    jdbcTemplate.update(
        "UPDATE subscriptions SET status=?, expire_at=? WHERE store_id=?",
        sub.status,
        Timestamp.from(sub.expireAt),
        storeId);
  }

  public void chargeStoreSubscription(String storeId) {
    Pricing pricing = getPricing();
    Wallet wallet = getWallet(storeId);
    if (wallet.balance < pricing.storeMonthlyFee) {
      return;
    }
    charge(storeId, "SUBSCRIPTION", "Monthly store subscription", pricing.storeMonthlyFee);
    renewSubscription(storeId, 30);
  }

  @Transactional
  public void recordAiCall(String storeId, long tokens) {
    Pricing pricing = getPricing();
    jdbcTemplate.update(
        "INSERT INTO store_ai_usage (store_id, ai_calls, ai_tokens) VALUES (?, ?, ?) "
            + "ON DUPLICATE KEY UPDATE ai_calls=ai_calls+1, ai_tokens=ai_tokens+?",
        storeId,
        1,
        tokens,
        tokens);
    charge(storeId, "AI_CALL", "AI usage", pricing.aiCallPrice);
  }

  public long getAiCalls(String storeId) {
    List<Long> list = jdbcTemplate.query(
        "SELECT ai_calls FROM store_ai_usage WHERE store_id=?",
        new Object[] { storeId },
        (rs, rowNum) -> rs.getLong("ai_calls"));
    return list.isEmpty() ? 0L : list.get(0);
  }

  public long getAiTokens(String storeId) {
    List<Long> list = jdbcTemplate.query(
        "SELECT ai_tokens FROM store_ai_usage WHERE store_id=?",
        new Object[] { storeId },
        (rs, rowNum) -> rs.getLong("ai_tokens"));
    return list.isEmpty() ? 0L : list.get(0);
  }

  public List<Map<String, Object>> listStoreSummaries() {
    return jdbcTemplate.query(
        "SELECT w.store_id, w.balance, s.status AS subscription_status, s.expire_at AS subscription_expire_at, "
            + "COALESCE(u.ai_calls, 0) AS ai_calls, COALESCE(u.ai_tokens, 0) AS ai_tokens "
            + "FROM wallets w "
            + "LEFT JOIN subscriptions s ON w.store_id = s.store_id "
            + "LEFT JOIN store_ai_usage u ON w.store_id = u.store_id",
        (rs, rowNum) -> Map.of(
            "storeId", rs.getString("store_id"),
            "balance", rs.getDouble("balance"),
            "subscriptionStatus", rs.getString("subscription_status"),
            "subscriptionExpireAt", rs.getTimestamp("subscription_expire_at"),
            "aiCalls", rs.getLong("ai_calls"),
            "aiTokens", rs.getLong("ai_tokens")
        ));
  }

  @Scheduled(cron = "0 0 0 * * *")
  public void autoRenewSubscriptions() {
    Instant now = Instant.now();
    List<Subscription> subs = jdbcTemplate.query(
        "SELECT store_id, status, expire_at FROM subscriptions",
        (rs, rowNum) -> {
          Subscription sub = new Subscription();
          sub.storeId = rs.getString("store_id");
          sub.status = rs.getString("status");
          Timestamp expireAt = rs.getTimestamp("expire_at");
          sub.expireAt = expireAt == null ? null : expireAt.toInstant();
          return sub;
        });
    for (Subscription sub : subs) {
      if (sub.expireAt == null) continue;
      long daysLeft = ChronoUnit.DAYS.between(now, sub.expireAt);
      if (daysLeft <= 7 && daysLeft >= 0 && "ACTIVE".equals(sub.status)) {
        chargeStoreSubscription(sub.storeId);
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
