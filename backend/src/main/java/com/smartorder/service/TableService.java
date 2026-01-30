package com.smartorder.service;

import com.smartorder.model.TableInfo;
import com.smartorder.model.TableStatus;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class TableService {
  private final Map<String, TableInfo> tables = new ConcurrentHashMap<>();

  public TableInfo getOrCreate(String tableNo) {
    return tables.computeIfAbsent(tableNo, key -> {
      TableInfo info = new TableInfo();
      info.tableNo = key;
      info.status = TableStatus.IDLE;
      return info;
    });
  }

  public void setStatus(String tableNo, TableStatus status) {
    TableInfo info = getOrCreate(tableNo);
    info.status = status;
  }

  public Map<String, TableInfo> listAll() {
    return tables;
  }
}
