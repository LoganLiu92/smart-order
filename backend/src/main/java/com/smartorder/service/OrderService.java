package com.smartorder.service;

import com.smartorder.api.dto.CreateOrderRequest;
import com.smartorder.api.dto.PaymentUpdateRequest;
import com.smartorder.api.dto.StatusUpdateRequest;
import com.smartorder.model.Order;
import com.smartorder.model.OrderItem;
import com.smartorder.model.OrderStatus;
import com.smartorder.model.PaymentStatus;
import com.smartorder.model.SelectedOption;
import com.smartorder.model.TableStatus;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {
  private final JdbcTemplate jdbcTemplate;
  private final TableService tableService;

  public OrderService(JdbcTemplate jdbcTemplate, TableService tableService) {
    this.jdbcTemplate = jdbcTemplate;
    this.tableService = tableService;
  }

  @Transactional
  public Order createOrder(CreateOrderRequest request) {
    Order order = new Order();
    order.orderId = UUID.randomUUID().toString();
    order.storeId = request.storeId;
    order.tableNo = request.tableNo;
    order.clientId = request.clientId;
    order.status = OrderStatus.NEW;
    order.paymentStatus = PaymentStatus.UNPAID;
    order.peopleCount = request.peopleCount;
    order.remark = request.remark;
    order.items = request.items == null ? new ArrayList<>() : request.items;
    order.totalAmount = calculateTotal(order.items);
    order.createdAt = Instant.now();
    order.updatedAt = order.createdAt;

    jdbcTemplate.update(
        "INSERT INTO orders (id, store_id, table_no, client_id, status, payment_status, people_count, remark, total_amount, created_at, updated_at) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
        order.orderId,
        order.storeId,
        order.tableNo,
        order.clientId,
        order.status.name(),
        order.paymentStatus.name(),
        order.peopleCount,
        order.remark,
        order.totalAmount,
        Timestamp.from(order.createdAt),
        Timestamp.from(order.updatedAt));

    for (OrderItem item : order.items) {
      long itemId = insertOrderItem(order.orderId, item);
      item.id = itemId;
      if (item.selectedOptions != null && !item.selectedOptions.isEmpty()) {
        for (SelectedOption option : item.selectedOptions) {
          jdbcTemplate.update(
              "INSERT INTO order_item_options (order_item_id, group_id, group_name, option_id, option_name, extra_price) "
                  + "VALUES (?, ?, ?, ?, ?, ?)",
              itemId,
              option.groupId,
              option.groupName,
              option.optionId,
              option.optionName,
              option.extraPrice == null ? BigDecimal.ZERO : option.extraPrice);
        }
      }
    }

    tableService.setStatus(order.storeId, order.tableNo, TableStatus.DINING);
    return order;
  }

  public List<Order> listOrders(String storeId, String tableNo, String status) {
    StringBuilder sql = new StringBuilder("SELECT * FROM orders WHERE 1=1");
    List<Object> args = new ArrayList<>();
    if (storeId != null && !storeId.isBlank()) {
      sql.append(" AND store_id=?");
      args.add(storeId);
    }
    if (tableNo != null && !tableNo.isBlank()) {
      sql.append(" AND table_no=?");
      args.add(tableNo);
    }
    if (status != null && !status.isBlank()) {
      sql.append(" AND status=?");
      args.add(status);
    }
    sql.append(" ORDER BY created_at DESC");

    List<Order> orders = jdbcTemplate.query(sql.toString(), args.toArray(), (rs, rowNum) -> {
      Order order = new Order();
      order.orderId = rs.getString("id");
      order.storeId = rs.getString("store_id");
      order.tableNo = rs.getString("table_no");
      order.clientId = rs.getString("client_id");
      order.status = OrderStatus.valueOf(rs.getString("status"));
      order.paymentStatus = PaymentStatus.valueOf(rs.getString("payment_status"));
      order.peopleCount = rs.getObject("people_count", Integer.class);
      order.remark = rs.getString("remark");
      order.totalAmount = rs.getBigDecimal("total_amount");
      order.createdAt = rs.getTimestamp("created_at").toInstant();
      order.updatedAt = rs.getTimestamp("updated_at").toInstant();
      Timestamp paidAt = rs.getTimestamp("paid_at");
      order.paidAt = paidAt == null ? null : paidAt.toInstant();
      order.paidBy = rs.getString("paid_by");
      Timestamp clearedAt = rs.getTimestamp("cleared_at");
      order.clearedAt = clearedAt == null ? null : clearedAt.toInstant();
      order.clearedBy = rs.getString("cleared_by");
      return order;
    });

    if (orders.isEmpty()) {
      return orders;
    }

    Map<String, Order> orderMap = orders.stream().collect(Collectors.toMap(o -> o.orderId, o -> o));
    String orderIdsIn = orders.stream().map(o -> "?").collect(Collectors.joining(","));
    List<Object> orderIdArgs = orders.stream().map(o -> o.orderId).collect(Collectors.toList());

    List<OrderItem> items = jdbcTemplate.query(
        "SELECT * FROM order_items WHERE order_id IN (" + orderIdsIn + ")",
        orderIdArgs.toArray(),
        (rs, rowNum) -> {
          OrderItem item = new OrderItem();
          item.id = rs.getLong("id");
          item.orderId = rs.getString("order_id");
          item.dishId = rs.getString("dish_id");
          item.dishName = rs.getString("dish_name");
          item.qty = rs.getInt("qty");
          item.unitPrice = rs.getBigDecimal("unit_price");
          item.lineTotal = rs.getBigDecimal("line_total");
          item.itemRemark = rs.getString("item_remark");
          item.selectedOptions = new ArrayList<>();
          return item;
        });

    Map<Long, OrderItem> itemMap = new HashMap<>();
    for (OrderItem item : items) {
      itemMap.put(item.id, item);
      Order order = orderMap.get(item.orderId);
      if (order != null) {
        order.items.add(item);
      }
    }

    if (!itemMap.isEmpty()) {
      String itemIdsIn = itemMap.keySet().stream().map(id -> "?").collect(Collectors.joining(","));
      List<Object> itemArgs = new ArrayList<>(itemMap.keySet());
      jdbcTemplate.query(
          "SELECT * FROM order_item_options WHERE order_item_id IN (" + itemIdsIn + ")",
          itemArgs.toArray(),
          (rs, rowNum) -> {
            long itemId = rs.getLong("order_item_id");
            OrderItem item = itemMap.get(itemId);
            if (item != null) {
              SelectedOption option = new SelectedOption();
              option.groupId = rs.getString("group_id");
              option.groupName = rs.getString("group_name");
              option.optionId = rs.getString("option_id");
              option.optionName = rs.getString("option_name");
              option.extraPrice = rs.getBigDecimal("extra_price");
              item.selectedOptions.add(option);
            }
            return null;
          });
    }

    return orders;
  }

  public Order updateStatus(String orderId, StatusUpdateRequest request) {
    Order order = requireOrder(orderId);
    order.status = request.status;
    order.updatedAt = Instant.now();
    jdbcTemplate.update(
        "UPDATE orders SET status=?, updated_at=? WHERE id=?",
        order.status.name(), Timestamp.from(order.updatedAt), order.orderId);
    return order;
  }

  public Order updatePayment(String orderId, PaymentUpdateRequest request) {
    Order order = requireOrder(orderId);
    order.paymentStatus = request.paymentStatus;
    order.paidBy = request.paidBy;
    order.paidAt = Instant.now();
    order.updatedAt = order.paidAt;

    jdbcTemplate.update(
        "UPDATE orders SET payment_status=?, paid_by=?, paid_at=?, updated_at=? WHERE id=?",
        order.paymentStatus.name(), order.paidBy, Timestamp.from(order.paidAt), Timestamp.from(order.updatedAt), order.orderId);

    if (order.paymentStatus == PaymentStatus.PAID) {
      tableService.setStatus(order.storeId, order.tableNo, TableStatus.TO_PAY);
    }
    return order;
  }

  public int settleTable(String storeId, String tableNo, String paidBy) {
    Instant now = Instant.now();
    int count = jdbcTemplate.update(
        "UPDATE orders SET payment_status=?, paid_by=?, paid_at=?, updated_at=? WHERE store_id=? AND table_no=? AND payment_status=?",
        PaymentStatus.PAID.name(),
        paidBy,
        Timestamp.from(now),
        Timestamp.from(now),
        storeId,
        tableNo,
        PaymentStatus.UNPAID.name());

    if (count > 0) {
      tableService.setStatus(storeId, tableNo, TableStatus.TO_PAY);
    }
    return count;
  }

  public void clearTable(String storeId, String tableNo, String clearedBy) {
    Instant now = Instant.now();
    jdbcTemplate.update(
        "UPDATE orders SET status=?, cleared_by=?, cleared_at=?, updated_at=? WHERE store_id=? AND table_no=? AND payment_status=?",
        OrderStatus.CLOSED.name(),
        clearedBy,
        Timestamp.from(now),
        Timestamp.from(now),
        storeId,
        tableNo,
        PaymentStatus.PAID.name());

    Integer openCount = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM orders WHERE store_id=? AND table_no=? AND status<>?",
        Integer.class,
        storeId,
        tableNo,
        OrderStatus.CLOSED.name());
    if (openCount != null && openCount > 0) {
      tableService.setStatus(storeId, tableNo, TableStatus.DINING);
    } else {
      tableService.setStatus(storeId, tableNo, TableStatus.IDLE);
    }
  }

  private Order requireOrder(String id) {
    List<Order> orders = jdbcTemplate.query(
        "SELECT * FROM orders WHERE id=?",
        new Object[] { id },
        (rs, rowNum) -> {
          Order order = new Order();
          order.orderId = rs.getString("id");
          order.storeId = rs.getString("store_id");
          order.tableNo = rs.getString("table_no");
          order.clientId = rs.getString("client_id");
          order.status = OrderStatus.valueOf(rs.getString("status"));
          order.paymentStatus = PaymentStatus.valueOf(rs.getString("payment_status"));
          order.peopleCount = rs.getObject("people_count", Integer.class);
          order.remark = rs.getString("remark");
          order.totalAmount = rs.getBigDecimal("total_amount");
          order.createdAt = rs.getTimestamp("created_at").toInstant();
          order.updatedAt = rs.getTimestamp("updated_at").toInstant();
          Timestamp paidAt = rs.getTimestamp("paid_at");
          order.paidAt = paidAt == null ? null : paidAt.toInstant();
          order.paidBy = rs.getString("paid_by");
          Timestamp clearedAt = rs.getTimestamp("cleared_at");
          order.clearedAt = clearedAt == null ? null : clearedAt.toInstant();
          order.clearedBy = rs.getString("cleared_by");
          return order;
        });
    if (orders.isEmpty()) {
      throw new IllegalArgumentException("Order not found: " + id);
    }
    Order order = orders.get(0);
    order.items = loadOrderItems(order.orderId);
    return order;
  }

  private List<OrderItem> loadOrderItems(String orderId) {
    List<OrderItem> items = jdbcTemplate.query(
        "SELECT * FROM order_items WHERE order_id=?",
        new Object[] { orderId },
        (rs, rowNum) -> {
          OrderItem item = new OrderItem();
          item.id = rs.getLong("id");
          item.orderId = rs.getString("order_id");
          item.dishId = rs.getString("dish_id");
          item.dishName = rs.getString("dish_name");
          item.qty = rs.getInt("qty");
          item.unitPrice = rs.getBigDecimal("unit_price");
          item.lineTotal = rs.getBigDecimal("line_total");
          item.itemRemark = rs.getString("item_remark");
          item.selectedOptions = new ArrayList<>();
          return item;
        });
    if (items.isEmpty()) return items;
    Map<Long, OrderItem> map = new HashMap<>();
    for (OrderItem item : items) {
      map.put(item.id, item);
    }
    String in = map.keySet().stream().map(id -> "?").collect(Collectors.joining(","));
    List<Object> args = new ArrayList<>(map.keySet());
    jdbcTemplate.query(
        "SELECT * FROM order_item_options WHERE order_item_id IN (" + in + ")",
        args.toArray(),
        (rs, rowNum) -> {
          long itemId = rs.getLong("order_item_id");
          OrderItem item = map.get(itemId);
          if (item != null) {
            SelectedOption option = new SelectedOption();
            option.groupId = rs.getString("group_id");
            option.groupName = rs.getString("group_name");
            option.optionId = rs.getString("option_id");
            option.optionName = rs.getString("option_name");
            option.extraPrice = rs.getBigDecimal("extra_price");
            item.selectedOptions.add(option);
          }
          return null;
        });
    return items;
  }

  private long insertOrderItem(String orderId, OrderItem item) {
    KeyHolder keyHolder = new GeneratedKeyHolder();
    jdbcTemplate.update(connection -> {
      PreparedStatement ps = connection.prepareStatement(
          "INSERT INTO order_items (order_id, dish_id, dish_name, qty, unit_price, line_total, item_remark) VALUES (?, ?, ?, ?, ?, ?, ?)",
          Statement.RETURN_GENERATED_KEYS);
      ps.setString(1, orderId);
      ps.setString(2, item.dishId);
      ps.setString(3, item.dishName);
      ps.setInt(4, item.qty == null ? 0 : item.qty);
      ps.setBigDecimal(5, item.unitPrice == null ? BigDecimal.ZERO : item.unitPrice);
      ps.setBigDecimal(6, item.lineTotal == null ? BigDecimal.ZERO : item.lineTotal);
      ps.setString(7, item.itemRemark);
      return ps;
    }, keyHolder);
    Number key = keyHolder.getKey();
    return key == null ? 0L : key.longValue();
  }

  private BigDecimal calculateTotal(List<OrderItem> items) {
    if (items == null) {
      return BigDecimal.ZERO;
    }
    BigDecimal total = BigDecimal.ZERO;
    for (OrderItem item : items) {
      if (item.lineTotal != null) {
        total = total.add(item.lineTotal);
      } else if (item.unitPrice != null && item.qty != null) {
        total = total.add(item.unitPrice.multiply(BigDecimal.valueOf(item.qty)));
      }
    }
    return total;
  }
}
