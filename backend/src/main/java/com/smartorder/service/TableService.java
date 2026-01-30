package com.smartorder.service;

import com.smartorder.model.TableInfo;
import com.smartorder.model.TableStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

@Service
public class TableService {
  private final JdbcTemplate jdbcTemplate;

  public TableService(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public TableInfo getOrCreate(String storeId, String tableNo) {
    List<TableInfo> list = jdbcTemplate.query(
        "SELECT store_id, table_no, status FROM tables WHERE store_id=? AND table_no=?",
        tableRowMapper(), storeId, tableNo);
    if (!list.isEmpty()) {
      return list.get(0);
    }
    jdbcTemplate.update(
        "INSERT INTO tables (store_id, table_no, status) VALUES (?, ?, ?)",
        storeId, tableNo, TableStatus.IDLE.name());
    TableInfo info = new TableInfo();
    info.storeId = storeId;
    info.tableNo = tableNo;
    info.status = TableStatus.IDLE;
    return info;
  }

  public void setStatus(String storeId, String tableNo, TableStatus status) {
    getOrCreate(storeId, tableNo);
    jdbcTemplate.update(
        "UPDATE tables SET status=? WHERE store_id=? AND table_no=?",
        status.name(), storeId, tableNo);
  }

  public List<TableInfo> listByStore(String storeId) {
    return jdbcTemplate.query(
        "SELECT store_id, table_no, status FROM tables WHERE store_id=? ORDER BY table_no",
        tableRowMapper(), storeId);
  }

  private RowMapper<TableInfo> tableRowMapper() {
    return (ResultSet rs, int rowNum) -> {
      TableInfo info = new TableInfo();
      info.storeId = rs.getString("store_id");
      info.tableNo = rs.getString("table_no");
      info.status = TableStatus.valueOf(rs.getString("status"));
      return info;
    };
  }
}
