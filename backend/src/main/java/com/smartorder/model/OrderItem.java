package com.smartorder.model;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class OrderItem {
  public Long id;
  public String orderId;
  public String dishId;
  public String dishName;
  public Integer qty;
  public BigDecimal unitPrice;
  public BigDecimal lineTotal;
  public List<SelectedOption> selectedOptions = new ArrayList<>();
  public String itemRemark;
}
