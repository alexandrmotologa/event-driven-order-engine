package com.engine.order.infrastructure.ratelimit;

import com.engine.order.infrastructure.multitenancy.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RateLimitingFilter extends OncePerRequestFilter {

    private final TokenBucketRateLimiterService rateLimiterService;

    public RateLimitingFilter(TokenBucketRateLimiterService rateLimiterService) {
        this.rateLimiterService = rateLimiterService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String path = request.getRequestURI();

        // Apply rate limiting specifically on business order endpoints
        if (rateLimiterService.isEnabled() && path.startsWith("/api/v1/orders") && !path.contains("/live")) {
            String tenantId = TenantContext.getTenantId();

            if (!rateLimiterService.tryAcquire(tenantId, 1)) {
                long retryAfterSeconds = rateLimiterService.getRetryAfterSeconds(tenantId, 1);

                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));

                String jsonError = String.format(
                        "{\"type\":\"https://api.engine.order.com/errors/rate-limit-exceeded\"," +
                                "\"title\":\"Too Many Requests\"," +
                                "\"status\":429," +
                                "\"detail\":\"Rate limit quota exceeded for tenant [%s]. Please retry in %d seconds.\"," +
                                "\"instance\":\"%s\"}",
                        tenantId, retryAfterSeconds, path
                );

                response.getWriter().write(jsonError);
                return;
            }
        }

        filterChain.doFilter(request, response);
    }
}
