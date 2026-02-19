package com.secfix.todos.security;

import com.secfix.todos.tenancy.TenantContext;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final String appEnv;

    public JwtAuthenticationFilter(JwtService jwtService, @Value("${sectool.env:dev}") String appEnv) {
        this.jwtService = jwtService;
        this.appEnv = appEnv;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            String authHeader = request.getHeader("Authorization");

            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);
                if (jwtService.isValid(token)) {
                    Claims claims = jwtService.parseToken(token);
                    UUID tenantId = UUID.fromString(claims.get("tenant_id", String.class));
                    Integer userId = claims.get("user_id", Integer.class);
                    String role = claims.get("role", String.class);

                    TenantContext.setTenantId(tenantId);
                    TenantContext.setUserId(userId);

                    var auth = new UsernamePasswordAuthenticationToken(
                            claims.getSubject(), null,
                            List.of(new SimpleGrantedAuthority("ROLE_" + role)));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            } else if ("dev".equals(appEnv)) {
                String tenantHeader = request.getHeader("X-Tenant-Id");
                if (tenantHeader != null) {
                    TenantContext.setTenantId(UUID.fromString(tenantHeader));
                    var auth = new UsernamePasswordAuthenticationToken("dev-user", null,
                            List.of(new SimpleGrantedAuthority("ROLE_USER")));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            }

            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }
}
