package com.smartorder.api.dto;

import com.smartorder.model.SelectedOption;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class CartItemRequest {
  public String storeId;
  public String tableNo;
  public String dishId;
  public String dishName;
  public Integer qty;
  public BigDecimal unitPrice;
  public List<SelectedOption> selectedOptions = new ArrayList<>();
}
