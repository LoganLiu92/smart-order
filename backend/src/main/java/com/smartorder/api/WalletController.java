package com.smartorder.api;

import com.smartorder.model.Subscription;
import com.smartorder.model.Wallet;
import com.smartorder.service.BillingService;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/wallet")
public class WalletController {
  private final BillingService billingService;

  public WalletController(BillingService billingService) {
    this.billingService = billingService;
  }

  @GetMapping("/{storeId}")
  public Map<String, Object> get(@PathVariable String storeId) {
    Wallet wallet = billingService.getWallet(storeId);
    Subscription sub = billingService.getSubscription(storeId);
    return Map.of(
        "storeId", storeId,
        "balance", wallet.balance,
        "ledger", wallet.ledger,
        "subscription", sub,
        "aiCalls", billingService.getAiCalls(storeId)
    );
  }
}
