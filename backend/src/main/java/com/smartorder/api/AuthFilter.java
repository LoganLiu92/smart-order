package com.smartorder.api;

import com.smartorder.service.JwtUtil;
import com.smartorder.service.JwtUtil.JwtClaims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class AuthFilter extends OncePerRequestFilter {
  private final JwtUtil jwtUtil;
  private static final Logger log = LoggerFactory.getLogger(AuthFilter.class);

  public AuthFilter(JwtUtil jwtUtil) {
    this.jwtUtil = jwtUtil;
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String path = request.getRequestURI();
    if (!path.startsWith("/api") || path.startsWith("/api/health") || path.startsWith("/api/auth")
        || path.startsWith("/api/platform/login")) {
      filterChain.doFilter(request, response);
      return;
    }

    if (isPublicApi(path, request.getMethod())) {
      filterChain.doFilter(request, response);
      return;
    }

    String token = resolveToken(request);
    if (token == null) {
      response.setStatus(HttpStatus.UNAUTHORIZED.value());
      return;
    }

    JwtClaims claims;
    try {
      claims = jwtUtil.parse(token);
    } catch (Exception ex) {
      response.setStatus(HttpStatus.UNAUTHORIZED.value());
      return;
    }

    if (!hasAccess(claims.role, path, request.getMethod())) {
      response.setStatus(HttpStatus.FORBIDDEN.value());
      log.warn("Forbidden: role={} path={} method={}", claims.role, path, request.getMethod());
      return;
    }

    String storeIdFromRequest = resolveStoreId(request, path);
    if (storeIdFromRequest != null && !"PLATFORM".equals(claims.role)
        && !storeIdFromRequest.equals(claims.storeId)) {
      response.setStatus(HttpStatus.FORBIDDEN.value());
      log.warn("Forbidden: storeId mismatch tokenStoreId={} reqStoreId={} path={}",
          claims.storeId, storeIdFromRequest, path);
      return;
    }

    request.setAttribute("auth.storeId", claims.storeId);
    request.setAttribute("auth.role", claims.role);
    request.setAttribute("auth.username", claims.username);

    filterChain.doFilter(request, response);
  }

  private boolean isPublicApi(String path, String method) {
    if (path.startsWith("/api/menu/") && method.equals("GET")) return true;
    if (path.startsWith("/api/store/") && method.equals("GET")) return true;
    if (path.startsWith("/api/q") && method.equals("GET")) return true;
    if (path.startsWith("/api/ai/recommend")) return true;
    if (path.startsWith("/api/cart")) return true;
    if (path.startsWith("/api/orders") && (method.equals("GET") || method.equals("POST"))) return true;
    if (path.startsWith("/api/session")) return true;
    if (path.startsWith("/ws")) return true;
    return false;
  }

  private boolean hasAccess(String role, String path, String method) {
    if (path.startsWith("/api/platform")) {
      return "PLATFORM".equals(role);
    }
    if (path.startsWith("/api/users") || path.startsWith("/api/wallet")) {
      return "ADMIN".equals(role);
    }
    if (path.startsWith("/api/menu/parse") || path.startsWith("/api/menu/ai-fill")
        || (path.startsWith("/api/menu/") && method.equals("PATCH"))) {
      return "ADMIN".equals(role);
    }
    if (path.startsWith("/api/tables")) {
      return "ADMIN".equals(role) || "CASHIER".equals(role);
    }
    if (path.startsWith("/api/orders") && method.equals("PATCH")) {
      if (path.contains("/status")) {
        return "ADMIN".equals(role) || "KITCHEN".equals(role);
      }
      if (path.contains("/payment")) {
        return "ADMIN".equals(role) || "CASHIER".equals(role);
      }
    }
    return true;
  }

  private String resolveStoreId(HttpServletRequest request, String path) {
    if (path.startsWith("/api/menu/parse") || path.startsWith("/api/menu/ai-fill")) {
      return null;
    }
    String storeId = request.getParameter("storeId");
    if (storeId != null && !storeId.isBlank()) {
      return storeId;
    }
    if (path.startsWith("/api/menu/") && path.split("/").length >= 4) {
      return path.split("/")[3];
    }
    if (path.startsWith("/api/wallet/") && path.split("/").length >= 4) {
      return path.split("/")[3];
    }
    return null;
  }

  private String resolveToken(HttpServletRequest request) {
    String header = request.getHeader("Authorization");
    if (header == null) return null;
    if (!header.startsWith("Bearer ")) return null;
    return header.substring("Bearer ".length()).trim();
  }
}
