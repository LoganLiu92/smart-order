package com.smartorder.service;

import com.smartorder.model.TableCode;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class TableCodeService {
  private final JdbcTemplate jdbcTemplate;

  public TableCodeService(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public TableCode bind(String storeId, String tableNo, String code) {
    jdbcTemplate.update("DELETE FROM table_codes WHERE store_id=? AND table_no=?", storeId, tableNo);
    jdbcTemplate.update("DELETE FROM table_codes WHERE code=?", code);
    jdbcTemplate.update(
        "INSERT INTO table_codes (store_id, table_no, code) VALUES (?, ?, ?)",
        storeId, tableNo, code);
    TableCode tc = new TableCode();
    tc.storeId = storeId;
    tc.tableNo = tableNo;
    tc.code = code;
    return tc;
  }

  public TableCode getByCode(String code) {
    List<TableCode> list = jdbcTemplate.query(
        "SELECT store_id, table_no, code FROM table_codes WHERE code=?",
        new Object[] { code },
        (rs, rowNum) -> {
          TableCode tc = new TableCode();
          tc.storeId = rs.getString("store_id");
          tc.tableNo = rs.getString("table_no");
          tc.code = rs.getString("code");
          return tc;
        });
    return list.isEmpty() ? null : list.get(0);
  }

  public TableCode getByTable(String storeId, String tableNo) {
    List<TableCode> list = jdbcTemplate.query(
        "SELECT store_id, table_no, code FROM table_codes WHERE store_id=? AND table_no=?",
        new Object[] { storeId, tableNo },
        (rs, rowNum) -> {
          TableCode tc = new TableCode();
          tc.storeId = rs.getString("store_id");
          tc.tableNo = rs.getString("table_no");
          tc.code = rs.getString("code");
          return tc;
        });
    return list.isEmpty() ? null : list.get(0);
  }

  public void unbind(String storeId, String tableNo) {
    jdbcTemplate.update("DELETE FROM table_codes WHERE store_id=? AND table_no=?", storeId, tableNo);
  }
}
