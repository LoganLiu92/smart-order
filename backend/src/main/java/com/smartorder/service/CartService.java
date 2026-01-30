package com.smartorder.service;

import com.smartorder.model.CartItem;
import com.smartorder.model.TableCart;
import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class CartService {
  private final Map<String, TableCart> carts = new ConcurrentHashMap<>();

  private String key(String storeId, String tableNo) {
    return storeId + ":" + tableNo;
  }

  public TableCart getCart(String storeId, String tableNo) {
    return carts.computeIfAbsent(key(storeId, tableNo), k -> {
      TableCart cart = new TableCart();
      cart.storeId = storeId;
      cart.tableNo = tableNo;
      return cart;
    });
  }

  public TableCart addItem(String storeId, String tableNo, String dishId, String dishName, BigDecimal unitPrice,
                           int qty, java.util.List<com.smartorder.model.SelectedOption> selectedOptions) {
    TableCart cart = getCart(storeId, tableNo);
    String signature = buildSignature(selectedOptions);
    CartItem existing = cart.items.stream()
        .filter(i -> dishId.equals(i.dishId) && signature.equals(i.optionSignature))
        .findFirst()
        .orElse(null);
    if (existing == null) {
      CartItem item = new CartItem();
      item.dishId = dishId;
      item.dishName = dishName;
      item.unitPrice = unitPrice;
      item.qty = qty;
      item.selectedOptions = selectedOptions == null ? new java.util.ArrayList<>() : selectedOptions;
      item.optionSignature = signature;
      cart.items.add(item);
    } else {
      existing.qty = existing.qty + qty;
    }
    return cart;
  }

  public TableCart updateQty(String storeId, String tableNo, String dishId, String optionSignature, int qty) {
    TableCart cart = getCart(storeId, tableNo);
    cart.items.removeIf(i -> i.qty == null || i.qty <= 0);
    for (int i = 0; i < cart.items.size(); i++) {
      CartItem item = cart.items.get(i);
      if (dishId.equals(item.dishId) && (optionSignature == null || optionSignature.equals(item.optionSignature))) {
        item.qty = qty;
      }
    }
    cart.items.removeIf(i -> i.qty == null || i.qty <= 0);
    return cart;
  }

  private String buildSignature(java.util.List<com.smartorder.model.SelectedOption> selectedOptions) {
    if (selectedOptions == null || selectedOptions.isEmpty()) {
      return "";
    }
    return selectedOptions.stream()
        .map(o -> o.optionId == null ? "" : o.optionId)
        .sorted()
        .reduce((a, b) -> a + "," + b)
        .orElse("");
  }

  public TableCart removeItem(String storeId, String tableNo, String dishId, String optionSignature) {
    TableCart cart = getCart(storeId, tableNo);
    cart.items.removeIf(i -> dishId.equals(i.dishId)
        && (optionSignature == null || optionSignature.equals(i.optionSignature)));
    return cart;
  }

  public TableCart clear(String storeId, String tableNo) {
    TableCart cart = getCart(storeId, tableNo);
    cart.items.clear();
    return cart;
  }
}
