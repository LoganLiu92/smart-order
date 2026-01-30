package com.smartorder.api;

import com.smartorder.model.StoreInfo;
import com.smartorder.service.StoreService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/store")
public class StoreController {
  private final StoreService storeService;

  public StoreController(StoreService storeService) {
    this.storeService = storeService;
  }

  @GetMapping("/{storeId}")
  public StoreInfo get(@PathVariable String storeId) {
    return storeService.get(storeId);
  }

  @PatchMapping("/{storeId}")
  public StoreInfo update(@PathVariable String storeId, @RequestBody StoreInfo request, HttpServletRequest http) {
    Object authStore = http.getAttribute("auth.storeId");
    if (authStore instanceof String authStoreId && !storeId.equals(authStoreId)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden");
    }
    return storeService.update(storeId, request);
  }
}
