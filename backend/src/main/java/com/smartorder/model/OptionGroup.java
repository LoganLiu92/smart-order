package com.smartorder.model;

import java.util.ArrayList;
import java.util.List;

public class OptionGroup {
  public String id;
  public String name;
  public boolean multiSelect;
  public List<OptionItem> items = new ArrayList<>();
}
