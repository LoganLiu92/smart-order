package com.smartorder.service;

import com.smartorder.model.StoreInfo;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class StoreService {
  private final JdbcTemplate jdbcTemplate;

  public StoreService(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public StoreInfo get(String storeId) {
    List<StoreInfo> list = jdbcTemplate.query(
        "SELECT id, name, status, default_language, logo_url, cover_url FROM stores WHERE id=?",
        new Object[] { storeId },
        (rs, rowNum) -> {
          StoreInfo info = new StoreInfo();
          info.id = rs.getString("id");
          info.name = rs.getString("name");
          info.status = rs.getString("status");
          info.defaultLanguage = rs.getString("default_language");
          info.logoUrl = rs.getString("logo_url");
          info.coverUrl = rs.getString("cover_url");
          return info;
        });
    if (!list.isEmpty()) {
      return list.get(0);
    }
    StoreInfo info = new StoreInfo();
    info.id = storeId;
    info.name = storeId;
    info.status = "ACTIVE";
    info.defaultLanguage = "zh-CN";
    jdbcTemplate.update(
        "INSERT INTO stores (id, name, status, default_language) VALUES (?, ?, ?, ?)",
        info.id, info.name, info.status, info.defaultLanguage);
    return info;
  }

  public StoreInfo update(String storeId, StoreInfo update) {
    StoreInfo info = get(storeId);
    if (update.name != null) info.name = update.name;
    if (update.logoUrl != null) info.logoUrl = update.logoUrl;
    if (update.coverUrl != null) info.coverUrl = update.coverUrl;
    if (update.status != null) info.status = update.status;
    if (update.defaultLanguage != null) info.defaultLanguage = update.defaultLanguage;

    jdbcTemplate.update(
        "UPDATE stores SET name=?, status=?, default_language=?, logo_url=?, cover_url=? WHERE id=?",
        info.name,
        info.status == null ? "ACTIVE" : info.status,
        info.defaultLanguage == null ? "zh-CN" : info.defaultLanguage,
        info.logoUrl,
        info.coverUrl,
        info.id);

    return info;
  }
}
