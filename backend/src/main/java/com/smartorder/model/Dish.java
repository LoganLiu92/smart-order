package com.smartorder.model;

import java.util.ArrayList;
import java.util.List;

public class Dish {
  public String id;
  public String categoryId;
  public String name;
  public java.math.BigDecimal price;
  public String description;
  public String imageUrl;
  public String detailImageUrl;
  public List<String> tags = new ArrayList<>();
  public String spicyLevel;
  public Integer calories;
  public String ingredients;
  public String allergens;
  public List<OptionGroup> optionGroups = new ArrayList<>();
}
