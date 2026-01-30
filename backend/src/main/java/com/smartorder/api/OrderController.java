package com.smartorder.api;

import com.smartorder.api.dto.CreateOrderRequest;
import com.smartorder.api.dto.PaymentUpdateRequest;
import com.smartorder.api.dto.StatusUpdateRequest;
import com.smartorder.model.Order;
import com.smartorder.service.OrderService;
import com.smartorder.ws.WsPublisher;
import java.util.List;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
public class OrderController {
  private final OrderService orderService;
  private final WsPublisher wsPublisher;

  public OrderController(OrderService orderService, WsPublisher wsPublisher) {
    this.orderService = orderService;
    this.wsPublisher = wsPublisher;
  }

  @PostMapping
  public Order create(@RequestBody CreateOrderRequest request) {
    Order order = orderService.createOrder(request);
    wsPublisher.publish("ORDER_CREATED", order);
    wsPublisher.publish("TABLE_UPDATED", request.tableNo);
    return order;
  }

  @GetMapping
  public List<Order> list(
      @RequestParam(required = false) String storeId,
      @RequestParam(required = false) String tableNo,
      @RequestParam(required = false) String status) {
    return orderService.listOrders(storeId, tableNo, status);
  }

  @PatchMapping("/{id}/status")
  public Order updateStatus(@PathVariable String id, @RequestBody StatusUpdateRequest request, HttpServletRequest http) {
    Order order = orderService.updateStatus(id, request);
    enforceStore(order, http);
    wsPublisher.publish("ORDER_UPDATED", order);
    return order;
  }

  @PatchMapping("/{id}/payment")
  public Order updatePayment(@PathVariable String id, @RequestBody PaymentUpdateRequest request, HttpServletRequest http) {
    Order order = orderService.updatePayment(id, request);
    enforceStore(order, http);
    wsPublisher.publish("ORDER_UPDATED", order);
    wsPublisher.publish("TABLE_UPDATED", order.tableNo);
    return order;
  }

  private void enforceStore(Order order, HttpServletRequest http) {
    Object storeAttr = http.getAttribute("auth.storeId");
    if (storeAttr instanceof String storeId && order != null && order.storeId != null) {
      if (!storeId.equals(order.storeId)) {
        throw new org.springframework.web.server.ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden");
      }
    }
  }
}
