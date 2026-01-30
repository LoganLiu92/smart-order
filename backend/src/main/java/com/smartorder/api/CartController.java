package com.smartorder.api;

import com.smartorder.api.dto.CartClearRequest;
import com.smartorder.api.dto.CartItemRequest;
import com.smartorder.api.dto.CartItemUpdateRequest;
import com.smartorder.model.TableCart;
import com.smartorder.service.CartService;
import com.smartorder.ws.WsPublisher;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/cart")
public class CartController {
  private final CartService cartService;
  private final WsPublisher wsPublisher;

  public CartController(CartService cartService, WsPublisher wsPublisher) {
    this.cartService = cartService;
    this.wsPublisher = wsPublisher;
  }

  @GetMapping
  public TableCart get(@RequestParam String storeId, @RequestParam String tableNo) {
    return cartService.getCart(storeId, tableNo);
  }

  @PostMapping("/items")
  public TableCart add(@RequestBody CartItemRequest request) {
    TableCart cart = cartService.addItem(
        request.storeId,
        request.tableNo,
        request.dishId,
        request.dishName,
        request.unitPrice,
        request.qty == null ? 1 : request.qty,
        request.selectedOptions
    );
    wsPublisher.publish("CART_UPDATED", cart);
    return cart;
  }

  @PatchMapping("/items")
  public TableCart update(@RequestBody CartItemUpdateRequest request) {
    TableCart cart = cartService.updateQty(
        request.storeId,
        request.tableNo,
        request.dishId,
        request.optionSignature,
        request.qty == null ? 0 : request.qty
    );
    wsPublisher.publish("CART_UPDATED", cart);
    return cart;
  }

  @DeleteMapping("/items")
  public TableCart remove(@RequestParam String storeId, @RequestParam String tableNo, @RequestParam String dishId,
                          @RequestParam(required = false) String optionSignature) {
    TableCart cart = cartService.removeItem(storeId, tableNo, dishId, optionSignature);
    wsPublisher.publish("CART_UPDATED", cart);
    return cart;
  }

  @PostMapping("/clear")
  public TableCart clear(@RequestBody CartClearRequest request) {
    TableCart cart = cartService.clear(request.storeId, request.tableNo);
    wsPublisher.publish("CART_UPDATED", cart);
    return cart;
  }
}
