package com.neptune.afo.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

import java.io.IOException;

@Configuration
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorsConfig implements Filter {

  @Override
  public void doFilter(
      ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain)
      throws IOException, ServletException {
    HttpServletRequest request = (HttpServletRequest) servletRequest;
    HttpServletResponse response = (HttpServletResponse) servletResponse;

    String origin = request.getHeader("Origin");
    String allowOrigin = origin != null ? origin : "*";

    response.setHeader("Access-Control-Allow-Origin", allowOrigin);
    response.setHeader("Access-control-Allow-Methods", "POST, PUT, GET, OPTIONS, DELETE");
    response.setHeader("Access-Control-Allow-Headers", "x-requested-with, x-auth-token");
    response.setHeader("Access-Control-Max-Age", "3600");
    response.setHeader("Access-Control-Allow-Credentials", "true");

    if (!(request.getMethod().equalsIgnoreCase("OPTIONS"))) {
      try {
        filterChain.doFilter(request, response);
      } catch (Exception ignored) {
      }
    } else {
      response.setHeader("Access-Control-Allowed-Methods", "POST, GET, DELETE,PUT");
      response.setHeader("Access-Control-Max-Age", "3600");
      response.setHeader(
          "Access-Control-Allow-Headers",
          "authorization, content-type, x-auth-token, "
              + "access-control-request-headers,access-control-request-method,accept,origin,authorization,Cookie,x-requested-with");
      response.setStatus(HttpServletResponse.SC_OK);
    }
  }
}
