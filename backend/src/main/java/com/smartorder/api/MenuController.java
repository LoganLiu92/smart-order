package com.smartorder.api;

import com.smartorder.api.dto.AiFillDishRequest;
import com.smartorder.api.dto.CreateCategoryRequest;
import com.smartorder.api.dto.CreateDishRequest;
import com.smartorder.api.dto.ParseMenuRequest;
import com.smartorder.api.dto.UpdateDishRequest;
import com.smartorder.model.Dish;
import com.smartorder.model.Menu;
import com.smartorder.model.MenuCategory;
import com.smartorder.service.BillingService;
import com.smartorder.service.MenuService;
import com.smartorder.service.OpenAiService;
import com.smartorder.ws.WsPublisher;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/menu")
public class MenuController {
  private final MenuService menuService;
  private final WsPublisher wsPublisher;
  private final OpenAiService openAiService;
  private final BillingService billingService;

  public MenuController(MenuService menuService, WsPublisher wsPublisher, OpenAiService openAiService, BillingService billingService) {
    this.menuService = menuService;
    this.wsPublisher = wsPublisher;
    this.openAiService = openAiService;
    this.billingService = billingService;
  }

  @GetMapping("/{storeId}")
  public Menu getMenu(@PathVariable String storeId) {
    return menuService.getMenu(storeId);
  }

  @PatchMapping("/{storeId}/dishes/{dishId}")
  public Dish updateDish(
      @PathVariable String storeId,
      @PathVariable String dishId,
      @RequestBody UpdateDishRequest request,
      HttpServletRequest http) {
    enforceStore(storeId, http);
    Dish update = new Dish();
    update.name = request.name;
    update.description = request.description;
    update.imageUrl = request.imageUrl;
    update.detailImageUrl = request.detailImageUrl;
    update.tags = request.tags;
    update.spicyLevel = request.spicyLevel;
    update.calories = request.calories;
    update.ingredients = request.ingredients;
    update.allergens = request.allergens;
    update.optionGroups = request.optionGroups;

    Dish updated = menuService.updateDish(storeId, dishId, update);
    if (updated == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Dish not found");
    }
    wsPublisher.publish("MENU_UPDATED", Map.of("storeId", storeId));
    return updated;
  }

  @PostMapping("/{storeId}/categories")
  public MenuCategory addCategory(@PathVariable String storeId, @RequestBody CreateCategoryRequest request, HttpServletRequest http) {
    enforceStore(storeId, http);
    return menuService.addCategory(storeId, request.name);
  }

  @PostMapping("/{storeId}/categories/{categoryId}/dishes")
  public Dish addDish(@PathVariable String storeId, @PathVariable String categoryId,
                      @RequestBody CreateDishRequest request, HttpServletRequest http) {
    enforceStore(storeId, http);
    Dish dish = new Dish();
    dish.name = request.name;
    dish.price = request.price;
    dish.description = request.description;
    dish.imageUrl = request.imageUrl;
    dish.detailImageUrl = request.detailImageUrl;
    dish.ingredients = request.ingredients;
    dish.allergens = request.allergens;
    dish.calories = request.calories;
    dish.tags = request.tags;
    dish.optionGroups = request.optionGroups;
    Dish created = menuService.addDish(storeId, categoryId, dish);
    if (created == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found");
    }
    wsPublisher.publish("MENU_UPDATED", Map.of("storeId", storeId));
    return created;
  }

  @PostMapping("/ai-fill")
  public Map<String, Object> aiFill(@RequestBody AiFillDishRequest request, HttpServletRequest http) throws Exception {
    enforceStore(request.storeId, http);
    if (!openAiService.isEnabled()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "OpenAI API key not configured");
    }
    if (!billingService.isSubscriptionActive(request.storeId)) {
      throw new ResponseStatusException(HttpStatus.PAYMENT_REQUIRED, "Subscription expired");
    }
    Dish dish = menuService.findDish(request.storeId, request.dishId);
    if (dish == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Dish not found");
    }
    Map<String, Object> response = openAiService.fillDishInfo(dish.name, dish.description);
    billingService.recordAiCall(request.storeId, extractTokens(response));
    return response;
  }

  @PostMapping("/parse")
  public Map<String, Object> parseMenu(@RequestBody ParseMenuRequest request, HttpServletRequest http) throws Exception {
    enforceStore(request.storeId, http);
    if (!openAiService.isEnabled()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "OpenAI API key not configured");
    }
    if (!billingService.isSubscriptionActive(request.storeId)) {
      throw new ResponseStatusException(HttpStatus.PAYMENT_REQUIRED, "Subscription expired");
    }
    Map<String, Object> result = openAiService.parseMenuImage(
        request.storeId, request.imageBase64, request.ocrText);
    billingService.recordAiCall(request.storeId, extractTokens(result));
    wsPublisher.publish("MENU_UPDATED", Map.of("storeId", request.storeId));
    return result;
  }

  private void enforceStore(String storeId, HttpServletRequest http) {
    Object authStore = http.getAttribute("auth.storeId");
    if (authStore instanceof String authStoreId && storeId != null && !storeId.equals(authStoreId)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden");
    }
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
