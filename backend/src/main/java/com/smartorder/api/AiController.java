package com.smartorder.api;

import com.smartorder.api.dto.AiRecommendRequest;
import com.smartorder.model.Menu;
import com.smartorder.service.BillingService;
import com.smartorder.service.MenuService;
import com.smartorder.service.OpenAiService;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/ai")
public class AiController {
  private final OpenAiService openAiService;
  private final MenuService menuService;
  private final BillingService billingService;

  public AiController(OpenAiService openAiService, MenuService menuService, BillingService billingService) {
    this.openAiService = openAiService;
    this.menuService = menuService;
    this.billingService = billingService;
  }

  @PostMapping("/recommend")
  public Map<String, Object> recommend(@RequestBody AiRecommendRequest request) throws Exception {
    if (!openAiService.isEnabled()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "OpenAI API key not configured");
    }
    if (!billingService.isSubscriptionActive(request.storeId)) {
      throw new ResponseStatusException(HttpStatus.PAYMENT_REQUIRED, "Subscription expired");
    }
    Menu menu = menuService.getMenu(request.storeId);
    Map<String, Object> response = openAiService.recommend(menu, request.message);
    billingService.recordAiCall(request.storeId, extractTokens(response));
    return response;
  }

  private long extractTokens(Map<String, Object> response) {
    if (response == null) return 0;
    Object usageObj = response.get("usage");
    if (!(usageObj instanceof Map)) return 0;
    Map<?, ?> usage = (Map<?, ?>) usageObj;
    Object total = usage.get("total_tokens");
    if (total instanceof Number) return ((Number) total).longValue();
    Object input = usage.get("input_tokens");
    Object output = usage.get("output_tokens");
    long inputVal = input instanceof Number ? ((Number) input).longValue() : 0;
    long outputVal = output instanceof Number ? ((Number) output).longValue() : 0;
    return inputVal + outputVal;
  }
}
