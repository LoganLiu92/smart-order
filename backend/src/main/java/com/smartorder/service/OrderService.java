package com.smartorder.service;

import com.smartorder.api.dto.CreateOrderRequest;
import com.smartorder.api.dto.PaymentUpdateRequest;
import com.smartorder.api.dto.StatusUpdateRequest;
import com.smartorder.model.Order;
import com.smartorder.model.OrderItem;
import com.smartorder.model.OrderStatus;
import com.smartorder.model.PaymentStatus;
import com.smartorder.model.TableStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class OrderService {
  private final Map<String, Order> orders = new ConcurrentHashMap<>();
  private final TableService tableService;

  public OrderService(TableService tableService) {
    this.tableService = tableService;
  }

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

    orders.put(order.orderId, order);
    tableService.setStatus(order.tableNo, TableStatus.DINING);

    return order;
  }

  public List<Order> listOrders(String storeId, String tableNo, String status) {
    return orders.values().stream()
        .filter(o -> storeId == null || storeId.isBlank() || storeId.equals(o.storeId))
        .filter(o -> tableNo == null || tableNo.isBlank() || tableNo.equals(o.tableNo))
        .filter(o -> status == null || status.isBlank() || status.equals(o.status.name()))
        .sorted((a, b) -> b.createdAt.compareTo(a.createdAt))
        .collect(Collectors.toList());
  }

  public Order updateStatus(String orderId, StatusUpdateRequest request) {
    Order order = requireOrder(orderId);
    order.status = request.status;
    order.updatedAt = Instant.now();
    return order;
  }

  public Order updatePayment(String orderId, PaymentUpdateRequest request) {
    Order order = requireOrder(orderId);
    order.paymentStatus = request.paymentStatus;
    order.paidBy = request.paidBy;
    order.paidAt = Instant.now();
    order.updatedAt = order.paidAt;

    if (order.paymentStatus == PaymentStatus.PAID) {
      tableService.setStatus(order.tableNo, TableStatus.TO_PAY);
    }

    return order;
  }

  public int settleTable(String tableNo, String paidBy) {
    Instant now = Instant.now();
    List<Order> tableOrders = orders.values().stream()
        .filter(o -> tableNo.equals(o.tableNo))
        .filter(o -> o.paymentStatus == PaymentStatus.UNPAID)
        .collect(Collectors.toList());

    for (Order order : tableOrders) {
      order.paymentStatus = PaymentStatus.PAID;
      order.paidBy = paidBy;
      order.paidAt = now;
      order.updatedAt = now;
    }

    if (!tableOrders.isEmpty()) {
      tableService.setStatus(tableNo, TableStatus.TO_PAY);
    }

    return tableOrders.size();
  }

  public void clearTable(String tableNo, String clearedBy) {
    List<Order> tableOrders = orders.values().stream()
        .filter(o -> tableNo.equals(o.tableNo))
        .filter(o -> o.paymentStatus == PaymentStatus.PAID)
        .collect(Collectors.toList());

    Instant now = Instant.now();
    for (Order order : tableOrders) {
      order.status = OrderStatus.CLOSED;
      order.clearedAt = now;
      order.clearedBy = clearedBy;
      order.updatedAt = now;
    }

    boolean hasOpenOrders = orders.values().stream()
        .anyMatch(o -> tableNo.equals(o.tableNo) && o.status != OrderStatus.CLOSED);
    tableService.setStatus(tableNo, hasOpenOrders ? TableStatus.DINING : TableStatus.IDLE);
  }

  private Order requireOrder(String id) {
    Order order = orders.get(id);
    if (order == null) {
      throw new IllegalArgumentException("Order not found: " + id);
    }
    return order;
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
