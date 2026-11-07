package com.FaceLit.backend.config;

import java.io.IOException;
import java.util.Set;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.web.filter.OncePerRequestFilter;
import com.FaceLit.backend.auth.service.serviceImpl.security.RecoveryRateLimiter;
import com.FaceLit.backend.auth.exception.RecoverySecurityException;

/** Uses the direct peer address; never trusts client-supplied forwarding headers. */
public class RecoveryRateLimitFilter extends OncePerRequestFilter {
    private static final Set<String> PATHS = Set.of("/api/auth/request-recovery", "/api/auth/verify-token", "/api/auth/reset-password");
    private final RecoveryRateLimiter limiter;
    public RecoveryRateLimitFilter(RecoveryRateLimiter limiter) { this.limiter = limiter; }
    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equals(request.getMethod()) || !PATHS.contains(request.getRequestURI().substring(request.getContextPath().length()));
    }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try { limiter.ip(request.getRemoteAddr()); }
        catch (RecoverySecurityException ex) {
            response.setStatus(429); response.setHeader("Retry-After", Long.toString(ex.getRetryAfter()));
            response.setContentType("application/json");
            response.getWriter().write("{\"message\":\"Demasiados intentos. Espera antes de volver a intentar.\"}"); return;
        }
        chain.doFilter(request, response);
    }
}
