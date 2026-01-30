package com.smartorder.api.dto;

import com.smartorder.model.OptionGroup;
import java.util.List;

public class UpdateDishRequest {
  public String name;
  public String description;
  public String imageUrl;
  public String detailImageUrl;
  public List<String> tags;
  public String spicyLevel;
  public Integer calories;
  public String ingredients;
  public String allergens;
  public List<OptionGroup> optionGroups;
}
