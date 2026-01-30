package com.smartorder.service;

import com.smartorder.model.Dish;
import com.smartorder.model.Menu;
import com.smartorder.model.MenuCategory;
import com.smartorder.model.OptionGroup;
import com.smartorder.model.OptionItem;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MenuService {
  private final JdbcTemplate jdbcTemplate;

  public MenuService(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public Menu getMenu(String storeId) {
    Menu menu = new Menu();
    menu.storeId = storeId;

    List<MenuCategory> categories = jdbcTemplate.query(
        "SELECT id, name FROM menu_categories WHERE store_id=? ORDER BY name",
        new Object[] { storeId },
        (rs, rowNum) -> {
          MenuCategory category = new MenuCategory();
          category.id = rs.getString("id");
          category.name = rs.getString("name");
          return category;
        });
    menu.categories = categories;

    if (categories.isEmpty()) {
      return menu;
    }

    String catIn = categories.stream().map(c -> "?").collect(Collectors.joining(","));
    List<Object> catArgs = categories.stream().map(c -> c.id).collect(Collectors.toList());

    List<Dish> dishes = jdbcTemplate.query(
        "SELECT * FROM dishes WHERE store_id=? AND category_id IN (" + catIn + ")",
        withArgs(storeId, catArgs),
        (rs, rowNum) -> {
          Dish dish = new Dish();
          dish.id = rs.getString("id");
          dish.name = rs.getString("name");
          dish.price = rs.getBigDecimal("price");
          dish.description = rs.getString("description");
          dish.imageUrl = rs.getString("image_url");
          dish.detailImageUrl = rs.getString("detail_image_url");
          dish.spicyLevel = rs.getString("spicy_level");
          dish.calories = rs.getObject("calories", Integer.class);
          dish.ingredients = rs.getString("ingredients");
          dish.allergens = rs.getString("allergens");
          String tags = rs.getString("tags");
          if (tags != null && !tags.isBlank()) {
            dish.tags = List.of(tags.split(",")).stream().map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toList());
          }
          dish.optionGroups = new ArrayList<>();
          dish.categoryId = rs.getString("category_id");
          return dish;
        });

    Map<String, MenuCategory> catMap = new HashMap<>();
    for (MenuCategory category : categories) {
      catMap.put(category.id, category);
    }

    Map<String, Dish> dishMap = new HashMap<>();
    for (Dish dish : dishes) {
      dishMap.put(dish.id, dish);
      MenuCategory category = catMap.get(dish.categoryId);
      if (category != null) {
        category.dishes.add(dish);
      }
    }

    if (!dishMap.isEmpty()) {
      String dishIn = dishMap.keySet().stream().map(id -> "?").collect(Collectors.joining(","));
      List<Object> dishArgs = new ArrayList<>(dishMap.keySet());
      List<OptionGroup> groups = jdbcTemplate.query(
          "SELECT * FROM option_groups WHERE dish_id IN (" + dishIn + ")",
          dishArgs.toArray(),
          (rs, rowNum) -> {
            OptionGroup group = new OptionGroup();
            group.id = rs.getString("id");
            group.name = rs.getString("name");
            group.multiSelect = rs.getBoolean("multi_select");
            group.items = new ArrayList<>();
            group.dishId = rs.getString("dish_id");
            return group;
          });

      Map<String, OptionGroup> groupMap = new HashMap<>();
      for (OptionGroup group : groups) {
        groupMap.put(group.id, group);
        Dish dish = dishMap.get(group.dishId);
        if (dish != null) {
          dish.optionGroups.add(group);
        }
      }

      if (!groupMap.isEmpty()) {
        String groupIn = groupMap.keySet().stream().map(id -> "?").collect(Collectors.joining(","));
        List<Object> groupArgs = new ArrayList<>(groupMap.keySet());
        jdbcTemplate.query(
            "SELECT * FROM option_items WHERE group_id IN (" + groupIn + ")",
            groupArgs.toArray(),
            (rs, rowNum) -> {
              OptionItem item = new OptionItem();
              item.id = rs.getString("id");
              item.name = rs.getString("name");
              item.extraPrice = rs.getBigDecimal("extra_price");
              String groupId = rs.getString("group_id");
              OptionGroup group = groupMap.get(groupId);
              if (group != null) {
                group.items.add(item);
              }
              return null;
            });
      }
    }

    return menu;
  }

  public void createEmptyMenu(String storeId) {
    getMenu(storeId);
  }

  public Dish findDish(String storeId, String dishId) {
    List<Dish> list = jdbcTemplate.query(
        "SELECT * FROM dishes WHERE store_id=? AND id=?",
        new Object[] { storeId, dishId },
        (rs, rowNum) -> {
          Dish dish = new Dish();
          dish.id = rs.getString("id");
          dish.name = rs.getString("name");
          dish.price = rs.getBigDecimal("price");
          dish.description = rs.getString("description");
          dish.imageUrl = rs.getString("image_url");
          dish.detailImageUrl = rs.getString("detail_image_url");
          dish.tags = tagsToList(rs.getString("tags"));
          dish.spicyLevel = rs.getString("spicy_level");
          dish.calories = rs.getObject("calories", Integer.class);
          dish.ingredients = rs.getString("ingredients");
          dish.allergens = rs.getString("allergens");
          dish.optionGroups = new ArrayList<>();
          dish.categoryId = rs.getString("category_id");
          return dish;
        });
    return list.isEmpty() ? null : list.get(0);
  }

  @Transactional
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

    jdbcTemplate.update(
        "UPDATE dishes SET name=?, description=?, image_url=?, detail_image_url=?, tags=?, spicy_level=?, calories=?, ingredients=?, allergens=? WHERE id=? AND store_id=?",
        dish.name,
        dish.description,
        dish.imageUrl,
        dish.detailImageUrl,
        tagsToString(dish.tags),
        dish.spicyLevel,
        dish.calories,
        dish.ingredients,
        dish.allergens,
        dishId,
        storeId);

    if (update.optionGroups != null) {
      replaceOptionGroups(dishId, update.optionGroups);
    }

    return dish;
  }

  public MenuCategory addCategory(String storeId, String name) {
    MenuCategory category = new MenuCategory();
    category.id = UUID.randomUUID().toString();
    category.name = name;
    jdbcTemplate.update(
        "INSERT INTO menu_categories (id, store_id, name) VALUES (?, ?, ?)",
        category.id, storeId, category.name);
    return category;
  }

  @Transactional
  public Dish addDish(String storeId, String categoryId, Dish dish) {
    dish.id = UUID.randomUUID().toString();
    jdbcTemplate.update(
        "INSERT INTO dishes (id, store_id, category_id, name, price, description, image_url, detail_image_url, tags, spicy_level, calories, ingredients, allergens) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
        dish.id,
        storeId,
        categoryId,
        dish.name,
        dish.price == null ? BigDecimal.ZERO : dish.price,
        dish.description,
        dish.imageUrl,
        dish.detailImageUrl,
        tagsToString(dish.tags),
        dish.spicyLevel,
        dish.calories,
        dish.ingredients,
        dish.allergens);

    if (dish.optionGroups != null && !dish.optionGroups.isEmpty()) {
      replaceOptionGroups(dish.id, dish.optionGroups);
    }
    return dish;
  }

  private void replaceOptionGroups(String dishId, List<OptionGroup> groups) {
    List<String> groupIds = jdbcTemplate.query(
        "SELECT id FROM option_groups WHERE dish_id=?",
        new Object[] { dishId },
        (rs, rowNum) -> rs.getString("id"));
    if (!groupIds.isEmpty()) {
      String in = groupIds.stream().map(id -> "?").collect(Collectors.joining(","));
      jdbcTemplate.update("DELETE FROM option_items WHERE group_id IN (" + in + ")", groupIds.toArray());
      jdbcTemplate.update("DELETE FROM option_groups WHERE dish_id=?", dishId);
    }

    for (OptionGroup group : groups) {
      String groupId = group.id == null ? UUID.randomUUID().toString() : group.id;
      jdbcTemplate.update(
          "INSERT INTO option_groups (id, dish_id, name, multi_select) VALUES (?, ?, ?, ?)",
          groupId,
          dishId,
          group.name,
          group.multiSelect);

      if (group.items != null) {
        for (OptionItem item : group.items) {
          String itemId = item.id == null ? UUID.randomUUID().toString() : item.id;
          jdbcTemplate.update(
              "INSERT INTO option_items (id, group_id, name, extra_price) VALUES (?, ?, ?, ?)",
              itemId,
              groupId,
              item.name,
              item.extraPrice == null ? BigDecimal.ZERO : item.extraPrice);
        }
      }
    }
  }

  private String tagsToString(List<String> tags) {
    if (tags == null || tags.isEmpty()) return null;
    return tags.stream().map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.joining(","));
  }

  private List<String> tagsToList(String tags) {
    if (tags == null || tags.isBlank()) return new ArrayList<>();
    return List.of(tags.split(",")).stream().map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toList());
  }

  private Object[] withArgs(String storeId, List<Object> args) {
    List<Object> list = new ArrayList<>();
    list.add(storeId);
    list.addAll(args);
    return list.toArray();
  }
}
