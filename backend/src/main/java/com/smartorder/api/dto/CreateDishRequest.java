package com.smartorder.api.dto;

import java.math.BigDecimal;
import java.util.List;
import com.smartorder.model.OptionGroup;

public class CreateDishRequest {
  public String name;
  public BigDecimal price;
  public String description;
  public String imageUrl;
  public String detailImageUrl;
  public String ingredients;
  public String allergens;
  public Integer calories;
  public List<String> tags;
  public List<OptionGroup> optionGroups;
}
