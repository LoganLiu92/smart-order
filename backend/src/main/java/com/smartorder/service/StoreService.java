package com.smartorder.service;

import com.smartorder.model.StoreInfo;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class StoreService {
  private final Map<String, StoreInfo> stores = new ConcurrentHashMap<>();

  public StoreService() {
  }

  public StoreInfo get(String storeId) {
    return stores.computeIfAbsent(storeId, id -> {
      StoreInfo info = new StoreInfo();
      info.id = id;
      info.name = id;
      return info;
    });
  }

  public StoreInfo update(String storeId, StoreInfo update) {
    StoreInfo info = get(storeId);
    if (update.name != null) info.name = update.name;
    if (update.logoUrl != null) info.logoUrl = update.logoUrl;
    if (update.coverUrl != null) info.coverUrl = update.coverUrl;
    return info;
  }
}
