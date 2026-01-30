package com.smartorder.service;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class TableSessionService {
  private final StringRedisTemplate redisTemplate;
  private final Duration ttl;

  public TableSessionService(StringRedisTemplate redisTemplate,
                             @Value("${app.session.lock-ttl-minutes:5}") long ttlMinutes) {
    this.redisTemplate = redisTemplate;
    this.ttl = Duration.ofMinutes(ttlMinutes);
  }

  private String key(String storeId, String tableNo) {
    return "session:table:" + storeId + ":" + tableNo;
  }

  public boolean isActive(String storeId, String tableNo) {
    String val = redisTemplate.opsForValue().get(key(storeId, tableNo));
    return "1".equals(val);
  }

  public void lock(String storeId, String tableNo) {
    redisTemplate.opsForValue().set(key(storeId, tableNo), "1", ttl);
  }

  public void unlock(String storeId, String tableNo) {
    redisTemplate.delete(key(storeId, tableNo));
  }
}
