package com.smartorder.api;

import com.smartorder.model.TableCode;
import com.smartorder.service.TableCodeService;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/q")
public class QrController {
  private final TableCodeService tableCodeService;

  public QrController(TableCodeService tableCodeService) {
    this.tableCodeService = tableCodeService;
  }

  @GetMapping
  public Map<String, String> resolve(@RequestParam String code) {
    TableCode tc = tableCodeService.getByCode(code);
    if (tc == null) {
      return Map.of("status", "NOT_FOUND");
    }
    return Map.of(
        "status", "OK",
        "storeId", tc.storeId,
        "tableNo", tc.tableNo
    );
  }
}
