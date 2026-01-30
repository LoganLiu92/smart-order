package com.smartorder.service;

import com.smartorder.model.Dish;
import com.smartorder.model.Menu;
import com.smartorder.model.MenuCategory;
import com.smartorder.model.OptionGroup;
import com.smartorder.model.OptionItem;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class MenuService {
  private final Map<String, Menu> menus = new ConcurrentHashMap<>();

  public MenuService() {
  }

  public Menu getMenu(String storeId) {
    return menus.computeIfAbsent(storeId, id -> {
      Menu menu = new Menu();
      menu.storeId = id;
      return menu;
    });
  }

  public void createEmptyMenu(String storeId) {
    menus.putIfAbsent(storeId, getMenu(storeId));
  }

  public Dish findDish(String storeId, String dishId) {
    Menu menu = getMenu(storeId);
    for (MenuCategory category : menu.categories) {
      for (Dish dish : category.dishes) {
        if (dishId.equals(dish.id)) {
          return dish;
        }
      }
    }
    return null;
  }

  public Dish updateDish(String storeId, String dishId, Dish update) {
    Dish dish = findDish(storeId, dishId);
    if (dish == null) {
      return null;
    }
    if (update.name != null) dish.name = update.name;
    if (update.description != null) dish.description = update.description;
    if (update.imageUrl != null) dish.imageUrl = update.imageUrl;
    if (update.detailImageUrl != null) dish.detailImageUrl = update.detailImageUrl;
    if (update.tags != null && !update.tags.isEmpty()) dish.tags = update.tags;
    if (update.spicyLevel != null) dish.spicyLevel = update.spicyLevel;
    if (update.calories != null) dish.calories = update.calories;
    if (update.ingredients != null) dish.ingredients = update.ingredients;
    if (update.allergens != null) dish.allergens = update.allergens;
    if (update.optionGroups != null) dish.optionGroups = update.optionGroups;
    return dish;
  }

  public MenuCategory addCategory(String storeId, String name) {
    Menu menu = getMenu(storeId);
    MenuCategory category = new MenuCategory();
    category.id = java.util.UUID.randomUUID().toString();
    category.name = name;
    menu.categories.add(category);
    return category;
  }

  public Dish addDish(String storeId, String categoryId, Dish dish) {
    Menu menu = getMenu(storeId);
    for (MenuCategory category : menu.categories) {
      if (categoryId.equals(category.id)) {
        dish.id = java.util.UUID.randomUUID().toString();
        category.dishes.add(dish);
        return dish;
      }
    }
    return null;
  }
}
