package com.engine.order.infrastructure.security;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

@Component
public class JwtTokenProvider {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenProvider.class);
    private static final String HMAC_SHA256 = "HmacSHA256";

    private final String secretKey;
    private final long tokenValidityMs;
    private final ObjectMapper objectMapper;

    public JwtTokenProvider(
            @Value("${app.security.jwt.secret:engine-order-processing-super-secure-jwt-hmac-secret-key-32b}") String secretKey,
            @Value("${app.security.jwt.validity-ms:86400000}") long tokenValidityMs,
            ObjectMapper objectMapper
    ) {
        this.secretKey = Objects.requireNonNull(secretKey, "secretKey must not be null");
        this.tokenValidityMs = tokenValidityMs;
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    public String generateToken(String username, String role) {
        try {
            String cleanRole = role.startsWith("ROLE_") ? role.substring(5) : role;

            Map<String, Object> header = Map.of(
                    "alg", "HS256",
                    "typ", "JWT"
            );

            long nowMs = Instant.now().toEpochMilli();
            long expMs = nowMs + tokenValidityMs;

            Map<String, Object> payload = Map.of(
                    "sub", username,
                    "role", cleanRole,
                    "roles", List.of("ROLE_" + cleanRole),
                    "iat", nowMs / 1000,
                    "exp", expMs / 1000
            );

            String encodedHeader = Base64.getUrlEncoder().withoutPadding().encodeToString(objectMapper.writeValueAsBytes(header));
            String encodedPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(objectMapper.writeValueAsBytes(payload));
            String dataToSign = encodedHeader + "." + encodedPayload;

            String signature = sign(dataToSign);
            return dataToSign + "." + signature;
        } catch (Exception e) {
            log.error("Failed to generate JWT token for user [{}]", username, e);
            throw new RuntimeException("Could not generate JWT token", e);
        }
    }

    public boolean validateToken(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                return false;
            }

            String dataToSign = parts[0] + "." + parts[1];
            String expectedSignature = sign(dataToSign);

            if (!MessageDigest.isEqual(parts[2].getBytes(StandardCharsets.UTF_8), expectedSignature.getBytes(StandardCharsets.UTF_8))) {
                log.warn("JWT token signature mismatch");
                return false;
            }

            byte[] payloadBytes = Base64.getUrlDecoder().decode(parts[1]);
            Map<String, Object> claims = objectMapper.readValue(payloadBytes, new TypeReference<>() {});

            if (claims.containsKey("exp")) {
                long exp = ((Number) claims.get("exp")).longValue();
                if (Instant.now().getEpochSecond() > exp) {
                    log.warn("JWT token is expired");
                    return false;
                }
            }

            return true;
        } catch (Exception e) {
            log.warn("Invalid JWT token format or signature: {}", e.getMessage());
            return false;
        }
    }

    public Authentication getAuthentication(String token) {
        try {
            String[] parts = token.split("\\.");
            byte[] payloadBytes = Base64.getUrlDecoder().decode(parts[1]);
            Map<String, Object> claims = objectMapper.readValue(payloadBytes, new TypeReference<>() {});

            String username = (String) claims.get("sub");
            List<GrantedAuthority> authorities = new ArrayList<>();

            if (claims.containsKey("roles") && claims.get("roles") instanceof List<?> roleList) {
                for (Object r : roleList) {
                    authorities.add(new SimpleGrantedAuthority(r.toString()));
                }
            } else if (claims.containsKey("role")) {
                String r = claims.get("role").toString();
                String fullRole = r.startsWith("ROLE_") ? r : "ROLE_" + r;
                authorities.add(new SimpleGrantedAuthority(fullRole));
            }

            return new UsernamePasswordAuthenticationToken(username, token, authorities);
        } catch (Exception e) {
            log.error("Failed to parse authentication from JWT token", e);
            return null;
        }
    }

    private String sign(String data) throws Exception {
        Mac mac = Mac.getInstance(HMAC_SHA256);
        SecretKeySpec secretKeySpec = new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), HMAC_SHA256);
        mac.init(secretKeySpec);
        byte[] rawHmac = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(rawHmac);
    }
}
