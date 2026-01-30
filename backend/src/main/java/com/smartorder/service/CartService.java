package com.smartorder.service;

import com.smartorder.model.CartItem;
import com.smartorder.model.SelectedOption;
import com.smartorder.model.TableCart;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CartService {
  private final JdbcTemplate jdbcTemplate;

  public CartService(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public TableCart getCart(String storeId, String tableNo) {
    Long cartId = getOrCreateCartId(storeId, tableNo);
    TableCart cart = new TableCart();
    cart.storeId = storeId;
    cart.tableNo = tableNo;
    cart.items = loadItems(cartId);
    return cart;
  }

  @Transactional
  public TableCart addItem(String storeId, String tableNo, String dishId, String dishName, BigDecimal unitPrice,
                           int qty, java.util.List<SelectedOption> selectedOptions) {
    Long cartId = getOrCreateCartId(storeId, tableNo);
    String signature = buildSignature(selectedOptions);
    List<CartItem> items = jdbcTemplate.query(
        "SELECT * FROM cart_items WHERE cart_id=? AND dish_id=? AND option_signature=?",
        new Object[] { cartId, dishId, signature },
        (rs, rowNum) -> {
          CartItem item = new CartItem();
          item.id = rs.getLong("id");
          item.dishId = rs.getString("dish_id");
          item.dishName = rs.getString("dish_name");
          item.unitPrice = rs.getBigDecimal("unit_price");
          item.qty = rs.getInt("qty");
          item.optionSignature = rs.getString("option_signature");
          item.selectedOptions = new ArrayList<>();
          return item;
        });

    if (items.isEmpty()) {
      long itemId = insertCartItem(cartId, dishId, dishName, unitPrice, qty, signature);
      if (selectedOptions != null) {
        for (SelectedOption option : selectedOptions) {
          jdbcTemplate.update(
              "INSERT INTO cart_item_options (cart_item_id, group_id, group_name, option_id, option_name, extra_price) "
                  + "VALUES (?, ?, ?, ?, ?, ?)",
              itemId,
              option.groupId,
              option.groupName,
              option.optionId,
              option.optionName,
              option.extraPrice == null ? BigDecimal.ZERO : option.extraPrice);
        }
      }
    } else {
      CartItem existing = items.get(0);
      jdbcTemplate.update(
          "UPDATE cart_items SET qty=? WHERE id=?",
          existing.qty + qty,
          existing.id);
    }
    touchCart(cartId);
    return getCart(storeId, tableNo);
  }

  @Transactional
  public TableCart updateQty(String storeId, String tableNo, String dishId, String optionSignature, int qty) {
    Long cartId = getOrCreateCartId(storeId, tableNo);
    String signature = optionSignature == null ? "" : optionSignature;
    List<Long> ids = jdbcTemplate.query(
        "SELECT id FROM cart_items WHERE cart_id=? AND dish_id=? AND option_signature=?",
        new Object[] { cartId, dishId, signature },
        (rs, rowNum) -> rs.getLong("id"));
    if (ids.isEmpty()) {
      return getCart(storeId, tableNo);
    }
    long itemId = ids.get(0);
    if (qty <= 0) {
      jdbcTemplate.update("DELETE FROM cart_item_options WHERE cart_item_id=?", itemId);
      jdbcTemplate.update("DELETE FROM cart_items WHERE id=?", itemId);
    } else {
      jdbcTemplate.update("UPDATE cart_items SET qty=? WHERE id=?", qty, itemId);
    }
    touchCart(cartId);
    return getCart(storeId, tableNo);
  }

  @Transactional
  public TableCart removeItem(String storeId, String tableNo, String dishId, String optionSignature) {
    Long cartId = getOrCreateCartId(storeId, tableNo);
    String signature = optionSignature == null ? "" : optionSignature;
    List<Long> ids = jdbcTemplate.query(
        "SELECT id FROM cart_items WHERE cart_id=? AND dish_id=? AND option_signature=?",
        new Object[] { cartId, dishId, signature },
        (rs, rowNum) -> rs.getLong("id"));
    for (Long id : ids) {
      jdbcTemplate.update("DELETE FROM cart_item_options WHERE cart_item_id=?", id);
      jdbcTemplate.update("DELETE FROM cart_items WHERE id=?", id);
    }
    touchCart(cartId);
    return getCart(storeId, tableNo);
  }

  @Transactional
  public TableCart clear(String storeId, String tableNo) {
    Long cartId = getOrCreateCartId(storeId, tableNo);
    List<Long> ids = jdbcTemplate.query(
        "SELECT id FROM cart_items WHERE cart_id=?",
        new Object[] { cartId },
        (rs, rowNum) -> rs.getLong("id"));
    if (!ids.isEmpty()) {
      String in = ids.stream().map(id -> "?").collect(Collectors.joining(","));
      jdbcTemplate.update("DELETE FROM cart_item_options WHERE cart_item_id IN (" + in + ")", ids.toArray());
      jdbcTemplate.update("DELETE FROM cart_items WHERE cart_id=?", cartId);
    }
    touchCart(cartId);
    return getCart(storeId, tableNo);
  }

  private List<CartItem> loadItems(Long cartId) {
    List<CartItem> items = jdbcTemplate.query(
        "SELECT * FROM cart_items WHERE cart_id=?",
        new Object[] { cartId },
        (rs, rowNum) -> {
          CartItem item = new CartItem();
          item.id = rs.getLong("id");
          item.dishId = rs.getString("dish_id");
          item.dishName = rs.getString("dish_name");
          item.unitPrice = rs.getBigDecimal("unit_price");
          item.qty = rs.getInt("qty");
          item.optionSignature = rs.getString("option_signature");
          item.selectedOptions = new ArrayList<>();
          return item;
        });

    if (items.isEmpty()) {
      return items;
    }

    Map<Long, CartItem> map = new HashMap<>();
    for (CartItem item : items) {
      map.put(item.id, item);
    }

    String in = map.keySet().stream().map(id -> "?").collect(Collectors.joining(","));
    List<Object> args = new ArrayList<>(map.keySet());
    jdbcTemplate.query(
        "SELECT * FROM cart_item_options WHERE cart_item_id IN (" + in + ")",
        args.toArray(),
        (rs, rowNum) -> {
          long itemId = rs.getLong("cart_item_id");
          CartItem item = map.get(itemId);
          if (item != null) {
            SelectedOption option = new SelectedOption();
            option.groupId = rs.getString("group_id");
            option.groupName = rs.getString("group_name");
            option.optionId = rs.getString("option_id");
            option.optionName = rs.getString("option_name");
            option.extraPrice = rs.getBigDecimal("extra_price");
            item.selectedOptions.add(option);
          }
          return null;
        });

    return items;
  }

  private Long getOrCreateCartId(String storeId, String tableNo) {
    List<Long> ids = jdbcTemplate.query(
        "SELECT id FROM carts WHERE store_id=? AND table_no=?",
        new Object[] { storeId, tableNo },
        (rs, rowNum) -> rs.getLong("id"));
    if (!ids.isEmpty()) {
      return ids.get(0);
    }
    KeyHolder keyHolder = new GeneratedKeyHolder();
    jdbcTemplate.update(connection -> {
      PreparedStatement ps = connection.prepareStatement(
          "INSERT INTO carts (store_id, table_no, updated_at) VALUES (?, ?, ?)",
          Statement.RETURN_GENERATED_KEYS);
      ps.setString(1, storeId);
      ps.setString(2, tableNo);
      ps.setTimestamp(3, Timestamp.from(Instant.now()));
      return ps;
    }, keyHolder);
    Number key = keyHolder.getKey();
    return key == null ? 0L : key.longValue();
  }

  private long insertCartItem(Long cartId, String dishId, String dishName, BigDecimal unitPrice, int qty, String signature) {
    KeyHolder keyHolder = new GeneratedKeyHolder();
    jdbcTemplate.update(connection -> {
      PreparedStatement ps = connection.prepareStatement(
          "INSERT INTO cart_items (cart_id, dish_id, dish_name, qty, unit_price, option_signature) VALUES (?, ?, ?, ?, ?, ?)",
          Statement.RETURN_GENERATED_KEYS);
      ps.setLong(1, cartId);
      ps.setString(2, dishId);
      ps.setString(3, dishName);
      ps.setInt(4, qty);
      ps.setBigDecimal(5, unitPrice == null ? BigDecimal.ZERO : unitPrice);
      ps.setString(6, signature);
      return ps;
    }, keyHolder);
    Number key = keyHolder.getKey();
    return key == null ? 0L : key.longValue();
  }

  private void touchCart(Long cartId) {
    jdbcTemplate.update("UPDATE carts SET updated_at=? WHERE id=?", Timestamp.from(Instant.now()), cartId);
  }

  private String buildSignature(java.util.List<SelectedOption> selectedOptions) {
    if (selectedOptions == null || selectedOptions.isEmpty()) {
      return "";
    }
    return selectedOptions.stream()
        .map(o -> o.optionId == null ? "" : o.optionId)
        .sorted()
        .reduce((a, b) -> a + "," + b)
        .orElse("");
  }
}
