package com.smartorder.api;

import com.smartorder.service.TableSessionService;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/session")
public class SessionController {
  private final TableSessionService sessionService;

  public SessionController(TableSessionService sessionService) {
    this.sessionService = sessionService;
  }

  @GetMapping
  public Map<String, Object> status(@RequestParam String storeId, @RequestParam String tableNo) {
    return Map.of("active", sessionService.isActive(storeId, tableNo));
  }

  @PostMapping("/lock")
  public Map<String, Object> lock(@RequestBody Map<String, String> request) {
    sessionService.lock(request.get("storeId"), request.get("tableNo"));
    return Map.of("active", true);
  }

  @PostMapping("/unlock")
  public Map<String, Object> unlock(@RequestBody Map<String, String> request) {
    sessionService.unlock(request.get("storeId"), request.get("tableNo"));
    return Map.of("active", false);
  }
}
