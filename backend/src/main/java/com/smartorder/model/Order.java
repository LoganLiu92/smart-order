package com.smartorder.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class Order {
  public String orderId;
  public String storeId;
  public String tableNo;
  public String clientId;
  public OrderStatus status;
  public PaymentStatus paymentStatus;
  public Integer peopleCount;
  public String remark;
  public BigDecimal totalAmount;
  public Instant createdAt;
  public Instant updatedAt;
  public Instant paidAt;
  public String paidBy;
  public Instant clearedAt;
  public String clearedBy;
  public List<OrderItem> items = new ArrayList<>();
}
