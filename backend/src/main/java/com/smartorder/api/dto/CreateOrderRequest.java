package com.smartorder.api.dto;

import com.smartorder.model.OrderItem;
import java.util.ArrayList;
import java.util.List;

public class CreateOrderRequest {
  public String storeId;
  public String tableNo;
  public Integer peopleCount;
  public String remark;
  public String clientId;
  public List<OrderItem> items = new ArrayList<>();
}
