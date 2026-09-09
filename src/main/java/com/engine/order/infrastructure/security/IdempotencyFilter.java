package com.engine.order.infrastructure.security;

import com.engine.order.domain.port.out.IdempotencyStorePort;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

@Component
@Order(1)
public class IdempotencyFilter extends OncePerRequestFilter {

    public static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    private final IdempotencyStorePort idempotencyStorePort;

    public IdempotencyFilter(IdempotencyStorePort idempotencyStorePort) {
        this.idempotencyStorePort = idempotencyStorePort;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String idempotencyKey = request.getHeader(IDEMPOTENCY_KEY_HEADER);

        if (idempotencyKey == null || idempotencyKey.isBlank() || !"POST".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        String trimmedKey = idempotencyKey.trim();

        // 1. Check if already processed
        Optional<IdempotencyStorePort.CachedResponse> cachedResponse = idempotencyStorePort.getCachedResponse(trimmedKey);
        if (cachedResponse.isPresent()) {
            IdempotencyStorePort.CachedResponse cached = cachedResponse.get();
            response.setStatus(cached.statusCode());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write(cached.responsePayload());
            response.getWriter().flush();
            return;
        }

        // 2. Try lock for concurrent execution
        boolean locked = idempotencyStorePort.tryLock(trimmedKey);
        if (!locked) {
            response.setStatus(HttpStatus.CONFLICT.value());
            response.setContentType("application/problem+json");
            response.getWriter().write("""
                    {
                      "type": "https://api.engine.order.com/errors/concurrent-request",
                      "title": "Conflict",
                      "status": 409,
                      "detail": "A request with this Idempotency-Key is currently being processed."
                    }
                    """);
            response.getWriter().flush();
            return;
        }

        ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(response);
        try {
            filterChain.doFilter(request, responseWrapper);

            int status = responseWrapper.getStatus();
            byte[] content = responseWrapper.getContentAsByteArray();
            String responseBody = new String(content, StandardCharsets.UTF_8);

            // Cache successful or business responses (e.g. 2xx or 4xx domain errors)
            if (status < 500) {
                idempotencyStorePort.saveResult(trimmedKey, status, responseBody);
            } else {
                idempotencyStorePort.unlock(trimmedKey);
            }

            responseWrapper.copyBodyToResponse();
        } catch (Exception ex) {
            idempotencyStorePort.unlock(trimmedKey);
            throw ex;
        }
    }
}
