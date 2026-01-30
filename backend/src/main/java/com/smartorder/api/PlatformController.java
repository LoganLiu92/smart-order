package com.smartorder.api;

import com.smartorder.api.dto.PlatformLoginRequest;
import com.smartorder.api.dto.PricingUpdateRequest;
import com.smartorder.api.dto.RenewRequest;
import com.smartorder.api.dto.TopupRequest;
import com.smartorder.model.Pricing;
import com.smartorder.service.AuthService;
import com.smartorder.service.BillingService;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/platform")
public class PlatformController {
  private final BillingService billingService;
  private final AuthService authService;

  public PlatformController(BillingService billingService, AuthService authService) {
    this.billingService = billingService;
    this.authService = authService;
  }

  @PostMapping("/login")
  public Map<String, Object> login(@RequestBody PlatformLoginRequest request) {
    if (!"admin".equals(request.username) || !"admin123".equals(request.password)) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
    }
    AuthService.Tokens tokens = authService.issueTokens("platform", request.username, "PLATFORM");
    return Map.of(
        "accessToken", tokens.accessToken,
        "refreshToken", tokens.refreshToken,
        "expiresIn", tokens.expiresInSeconds
    );
  }

  @GetMapping("/pricing")
  public Pricing pricing() {
    return billingService.getPricing();
  }

  @PostMapping("/pricing")
  public Pricing updatePricing(@RequestBody PricingUpdateRequest request) {
    Pricing pricing = new Pricing();
    pricing.platformMonthlyFee = request.platformMonthlyFee;
    pricing.storeMonthlyFee = request.storeMonthlyFee;
    pricing.aiCallPrice = request.aiCallPrice;
    return billingService.updatePricing(pricing);
  }

  @GetMapping("/stores")
  public Object stores() {
    return billingService.listStoreSummaries();
  }

  @PostMapping("/stores/{storeId}/topup")
  public Object topup(@PathVariable String storeId, @RequestBody TopupRequest request) {
    return billingService.topup(storeId, request.amount, request.reason == null ? "Manual topup" : request.reason);
  }

  @PostMapping("/stores/{storeId}/subscription/pause")
  public void pause(@PathVariable String storeId) {
    billingService.pauseSubscription(storeId);
  }

  @PostMapping("/stores/{storeId}/subscription/renew")
  public void renew(@PathVariable String storeId, @RequestBody RenewRequest request) {
    int days = request.days <= 0 ? 30 : request.days;
    billingService.renewSubscription(storeId, days);
  }

  @PostMapping("/stores/{storeId}/subscription/charge")
  public void charge(@PathVariable String storeId) {
    billingService.chargeStoreSubscription(storeId);
  }
}
