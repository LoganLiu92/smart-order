package com.smartorder.api;

import com.smartorder.api.dto.BindTableCodeRequest;
import com.smartorder.api.dto.ClearTableRequest;
import com.smartorder.api.dto.SettleTableRequest;
import com.smartorder.model.TableCode;
import com.smartorder.model.TableInfo;
import com.smartorder.service.TableCodeService;
import com.smartorder.service.OrderService;
import com.smartorder.service.TableService;
import com.smartorder.ws.WsPublisher;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tables")
public class TableController {
  private final OrderService orderService;
  private final WsPublisher wsPublisher;
  private final TableService tableService;
  private final TableCodeService tableCodeService;

  public TableController(OrderService orderService, WsPublisher wsPublisher, TableService tableService, TableCodeService tableCodeService) {
    this.orderService = orderService;
    this.wsPublisher = wsPublisher;
    this.tableService = tableService;
    this.tableCodeService = tableCodeService;
  }

  @GetMapping
  public Object list(@RequestParam String storeId) {
    return tableService.listByStore(storeId).stream()
        .map(t -> {
          TableCode code = tableCodeService.getByTable(storeId, t.tableNo);
          return Map.of(
              "tableNo", t.tableNo,
              "status", t.status == null ? "IDLE" : t.status.name(),
              "code", code == null ? "" : code.code
          );
        })
        .collect(Collectors.toList());
  }

  @PostMapping
  public TableInfo create(@RequestBody BindTableCodeRequest request, HttpServletRequest http) {
    enforceStore(request.storeId, http);
    TableInfo info = tableService.getOrCreate(request.storeId, request.tableNo);
    if (request.code != null && !request.code.isBlank()) {
      tableCodeService.bind(request.storeId, request.tableNo, request.code);
    }
    return info;
  }

  @PostMapping("/bind")
  public TableCode bind(@RequestBody BindTableCodeRequest request, HttpServletRequest http) {
    enforceStore(request.storeId, http);
    return tableCodeService.bind(request.storeId, request.tableNo, request.code);
  }

  @PostMapping("/unbind")
  public void unbind(@RequestBody BindTableCodeRequest request, HttpServletRequest http) {
    enforceStore(request.storeId, http);
    tableCodeService.unbind(request.storeId, request.tableNo);
  }
  @PostMapping("/{tableNo}/clear")
  public Map<String, String> clear(@PathVariable String tableNo, @RequestBody ClearTableRequest request, HttpServletRequest http) {
    enforceStore(request.storeId, http);
    orderService.clearTable(request.storeId, tableNo, request.clearedBy);
    wsPublisher.publish("TABLE_UPDATED", tableNo);
    return Map.of("status", "cleared", "tableNo", tableNo);
  }

  @PostMapping("/{tableNo}/settle")
  public Map<String, Object> settle(@PathVariable String tableNo, @RequestBody SettleTableRequest request, HttpServletRequest http) {
    enforceStore(request.storeId, http);
    int count = orderService.settleTable(request.storeId, tableNo, request.paidBy);
    wsPublisher.publish("TABLE_UPDATED", tableNo);
    wsPublisher.publish("ORDER_UPDATED", Map.of("tableNo", tableNo, "count", count));
    return Map.of("status", "settled", "tableNo", tableNo, "orders", count);
  }

  private void enforceStore(String storeId, HttpServletRequest http) {
    if (storeId == null) {
      throw new org.springframework.web.server.ResponseStatusException(
          org.springframework.http.HttpStatus.BAD_REQUEST, "storeId required");
    }
    Object authStore = http.getAttribute("auth.storeId");
    if (authStore instanceof String authStoreId && !storeId.equals(authStoreId)) {
      throw new org.springframework.web.server.ResponseStatusException(
          org.springframework.http.HttpStatus.FORBIDDEN, "Forbidden");
    }
  }
}
