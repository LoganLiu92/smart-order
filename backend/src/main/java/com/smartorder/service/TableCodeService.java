package com.smartorder.service;

import com.smartorder.model.TableCode;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class TableCodeService {
  private final Map<String, TableCode> codeMap = new ConcurrentHashMap<>();
  private final Map<String, TableCode> tableMap = new ConcurrentHashMap<>();

  private String tableKey(String storeId, String tableNo) {
    return storeId + ":" + tableNo;
  }

  public TableCode bind(String storeId, String tableNo, String code) {
    TableCode tc = new TableCode();
    tc.storeId = storeId;
    tc.tableNo = tableNo;
    tc.code = code;
    codeMap.put(code, tc);
    tableMap.put(tableKey(storeId, tableNo), tc);
    return tc;
  }

  public TableCode getByCode(String code) {
    return codeMap.get(code);
  }

  public TableCode getByTable(String storeId, String tableNo) {
    return tableMap.get(tableKey(storeId, tableNo));
  }

  public void unbind(String storeId, String tableNo) {
    TableCode existing = tableMap.remove(tableKey(storeId, tableNo));
    if (existing != null) {
      codeMap.remove(existing.code);
    }
  }
}
