package com.smartorder.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartorder.model.Menu;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class OpenAiService {
  private final ObjectMapper objectMapper;
  private final HttpClient httpClient;

  @Value("${app.openai.api-key:}")
  private String apiKey;

  @Value("${app.openai.base-url:https://api.openai.com/v1}")
  private String baseUrl;

  @Value("${app.openai.model.recommend:gpt-4.1-mini}")
  private String recommendModel;

  @Value("${app.openai.model.vision:gpt-4.1-mini}")
  private String visionModel;

  public OpenAiService(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
    this.httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();
  }

  public boolean isEnabled() {
    return apiKey != null && !apiKey.isBlank();
  }

  public Map<String, Object> recommend(Menu menu, String message) throws Exception {
    String menuJson = objectMapper.writeValueAsString(menu);
    String prompt = "You are a restaurant assistant. Recommend dishes from the menu JSON. "
        + "Return JSON with fields: recommendations: [{dishId, dishName, reason, options[]}]. "
        + "Menu: " + menuJson + "\nUser request: " + message;

    Map<String, Object> body = new HashMap<>();
    body.put("model", recommendModel);
    body.put("input", List.of(
        Map.of(
            "role", "user",
            "content", List.of(
                Map.of("type", "input_text", "text", prompt)
            )
        )
    ));

    return postResponses(body);
  }

  public Map<String, Object> parseMenuImage(String storeId, String imageBase64, String ocrText) throws Exception {
    String prompt = "Extract a structured menu from this image or text. "
        + "Return JSON with categories: [{name, dishes: [{name, price, description}]}].";

    Map<String, Object> content = new HashMap<>();
    content.put("type", "input_text");
    content.put("text", prompt + (ocrText == null ? "" : (" OCR: " + ocrText)));

    Map<String, Object> body = new HashMap<>();
    body.put("model", visionModel);
    body.put("input", List.of(
        Map.of(
            "role", "user",
            "content", imageBase64 == null ? List.of(content) : List.of(
                content,
                Map.of("type", "input_image", "image_base64", imageBase64)
            )
        )
    ));

    return postResponses(body);
  }

  public Map<String, Object> fillDishInfo(String name, String description) throws Exception {
    String prompt = "Generate structured fields for a menu item. "
        + "Return JSON with fields: ingredients (string), allergens (string), calories (number), tags (array of strings). "
        + "Dish name: " + name + ". Description: " + (description == null ? "" : description);

    Map<String, Object> body = new HashMap<>();
    body.put("model", recommendModel);
    body.put("input", List.of(
        Map.of(
            "role", "user",
            "content", List.of(
                Map.of("type", "input_text", "text", prompt)
            )
        )
    ));

    return postResponses(body);
  }

  private Map<String, Object> postResponses(Map<String, Object> body) throws Exception {
    String json = objectMapper.writeValueAsString(body);
    HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(baseUrl + "/responses"))
        .header("Authorization", "Bearer " + apiKey)
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(json))
        .build();

    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    if (response.statusCode() >= 300) {
      throw new IllegalStateException("OpenAI error: " + response.statusCode() + " " + response.body());
    }
    return objectMapper.readValue(response.body(), Map.class);
  }
}
