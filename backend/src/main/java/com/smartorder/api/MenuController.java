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
import java.util.Base64;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.multipart.MultipartFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/api/menu")
public class MenuController {
  private final MenuService menuService;
  private final WsPublisher wsPublisher;
  private final OpenAiService openAiService;
  private final BillingService billingService;
  private final ObjectMapper objectMapper;
  private static final Logger log = LoggerFactory.getLogger(MenuController.class);

  public MenuController(MenuService menuService, WsPublisher wsPublisher, OpenAiService openAiService, BillingService billingService,
                        ObjectMapper objectMapper) {
    this.menuService = menuService;
    this.wsPublisher = wsPublisher;
    this.openAiService = openAiService;
    this.billingService = billingService;
    this.objectMapper = objectMapper;
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
    request.storeId = resolveStoreId(request.storeId, http);
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
    request.storeId = resolveStoreId(request.storeId, http);
    enforceStore(request.storeId, http);
    if (!openAiService.isEnabled()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "OpenAI API key not configured");
    }
    if (!billingService.isSubscriptionActive(request.storeId)) {
      throw new ResponseStatusException(HttpStatus.PAYMENT_REQUIRED, "Subscription expired");
    }
    Map<String, Object> result = openAiService.parseMenuImage(
        request.storeId, request.imageBase64, request.ocrText);
    ParsedMenu parsed = parseMenuResponse(result);
    if (parsed == null) {
      log.warn("Menu parse failed: storeId={} reason=no-parseable-json", request.storeId);
    } else if (parsed.categories == null || parsed.categories.isEmpty()) {
      log.warn("Menu parse returned empty categories: storeId={}", request.storeId);
    } else {
      menuService.replaceMenu(request.storeId, toCategories(parsed));
      log.info("Menu parse saved: storeId={} categories={}", request.storeId, parsed.categories.size());
    }
    billingService.recordAiCall(request.storeId, extractTokens(result));
    wsPublisher.publish("MENU_UPDATED", Map.of("storeId", request.storeId));
    return result;
  }

  @PostMapping(value = "/parse-file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public Map<String, Object> parseMenuFile(
      @RequestParam(name = "storeId", required = false) String storeId,
      @RequestParam(name = "ocrText", required = false) String ocrText,
      @RequestParam(name = "file") MultipartFile file,
      HttpServletRequest http) throws Exception {
    String resolvedStoreId = resolveStoreId(storeId, http);
    enforceStore(resolvedStoreId, http);
    if (!openAiService.isEnabled()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "OpenAI API key not configured");
    }
    if (!billingService.isSubscriptionActive(resolvedStoreId)) {
      throw new ResponseStatusException(HttpStatus.PAYMENT_REQUIRED, "Subscription expired");
    }
    if (file == null || file.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Empty file");
    }
    String base64 = Base64.getEncoder().encodeToString(file.getBytes());
    Map<String, Object> result = openAiService.parseMenuImage(resolvedStoreId, base64, ocrText);
    ParsedMenu parsed = parseMenuResponse(result);
    if (parsed == null) {
      log.warn("Menu parse-file failed: storeId={} reason=no-parseable-json", resolvedStoreId);
    } else if (parsed.categories == null || parsed.categories.isEmpty()) {
      log.warn("Menu parse-file returned empty categories: storeId={}", resolvedStoreId);
    } else {
      menuService.replaceMenu(resolvedStoreId, toCategories(parsed));
      log.info("Menu parse-file saved: storeId={} categories={}", resolvedStoreId, parsed.categories.size());
    }
    billingService.recordAiCall(resolvedStoreId, extractTokens(result));
    wsPublisher.publish("MENU_UPDATED", Map.of("storeId", resolvedStoreId));
    return result;
  }

  private void enforceStore(String storeId, HttpServletRequest http) {
    Object authStore = http.getAttribute("auth.storeId");
    if (authStore instanceof String authStoreId && storeId != null && !storeId.equals(authStoreId)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden");
    }
  }

  private String resolveStoreId(String storeId, HttpServletRequest http) {
    if (storeId != null && !storeId.isBlank()) {
      return storeId;
    }
    Object authStore = http.getAttribute("auth.storeId");
    if (authStore instanceof String authStoreId) {
      return authStoreId;
    }
    return storeId;
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

  private ParsedMenu parseMenuResponse(Map<String, Object> response) {
    String text = extractResponseText(response);
    if (text == null || text.isBlank()) return null;
    String cleaned = text.trim();
    if (cleaned.startsWith("```")) {
      cleaned = cleaned.replaceAll("^```[a-zA-Z]*\\n?", "").replaceAll("```$", "").trim();
    }
    try {
      JsonNode root = objectMapper.readTree(cleaned);
      return parseMenuJson(root);
    } catch (Exception ex) {
      return null;
    }
  }

  private String extractResponseText(Map<String, Object> response) {
    if (response == null) return null;
    Object outputObj = response.get("output");
    if (!(outputObj instanceof List<?> output)) return null;
    for (Object item : output) {
      if (!(item instanceof Map<?, ?> msg)) continue;
      Object type = msg.get("type");
      if (!"message".equals(type)) continue;
      Object contentObj = msg.get("content");
      if (!(contentObj instanceof List<?> content)) continue;
      for (Object c : content) {
        if (!(c instanceof Map<?, ?> chunk)) continue;
        if ("output_text".equals(chunk.get("type"))) {
          Object text = chunk.get("text");
          if (text instanceof String) return (String) text;
        }
      }
    }
    return null;
  }

  private List<MenuCategory> toCategories(ParsedMenu parsed) {
    List<MenuCategory> categories = new ArrayList<>();
    if (parsed.categories == null) return categories;
    for (ParsedCategory pc : parsed.categories) {
      MenuCategory category = new MenuCategory();
      category.name = pc.name;
      category.dishes = new ArrayList<>();
      if (pc.dishes != null) {
        for (ParsedDish pd : pc.dishes) {
          Dish dish = new Dish();
          dish.name = pd.name;
          dish.description = pd.description;
          dish.price = pd.price;
          category.dishes.add(dish);
        }
      }
      categories.add(category);
    }
    return categories;
  }

  private ParsedMenu parseMenuJson(JsonNode root) {
    JsonNode categoriesNode = root.get("categories");
    if (categoriesNode == null || !categoriesNode.isArray()) return null;
    ParsedMenu menu = new ParsedMenu();
    menu.categories = new ArrayList<>();
    for (JsonNode catNode : categoriesNode) {
      ParsedCategory cat = new ParsedCategory();
      cat.name = textOrNull(catNode.get("name"));
      cat.dishes = new ArrayList<>();
      JsonNode dishesNode = catNode.get("dishes");
      if (dishesNode != null && dishesNode.isArray()) {
        for (JsonNode dishNode : dishesNode) {
          ParsedDish dish = new ParsedDish();
          dish.name = textOrNull(dishNode.get("name"));
          dish.description = textOrNull(dishNode.get("description"));
          dish.price = parsePrice(dishNode.get("price"));
          cat.dishes.add(dish);
        }
      }
      menu.categories.add(cat);
    }
    return menu;
  }

  private String textOrNull(JsonNode node) {
    if (node == null || node.isNull()) return null;
    return node.asText();
  }

  private java.math.BigDecimal parsePrice(JsonNode node) {
    if (node == null || node.isNull()) return null;
    if (node.isNumber()) {
      return node.decimalValue();
    }
    String raw = node.asText();
    if (raw == null) return null;
    String normalized = raw.replaceAll("[^0-9.]+", "");
    if (normalized.isBlank()) return null;
    try {
      return new java.math.BigDecimal(normalized);
    } catch (Exception ex) {
      return null;
    }
  }

  public static class ParsedMenu {
    public List<ParsedCategory> categories;
  }

  public static class ParsedCategory {
    public String name;
    public List<ParsedDish> dishes;
  }

  public static class ParsedDish {
    public String name;
    public java.math.BigDecimal price;
    public String description;
  }
}
