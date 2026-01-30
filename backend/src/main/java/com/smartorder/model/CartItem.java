package com.smartorder.model;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class CartItem {
  public Long id;
  public String dishId;
  public String dishName;
  public Integer qty;
  public BigDecimal unitPrice;
  public List<SelectedOption> selectedOptions = new ArrayList<>();
  public String optionSignature;
}
