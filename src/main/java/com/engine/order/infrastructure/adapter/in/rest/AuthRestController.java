package com.engine.order.infrastructure.adapter.in.rest;

import com.engine.order.infrastructure.adapter.in.rest.dto.AuthRequest;
import com.engine.order.infrastructure.adapter.in.rest.dto.AuthResponse;
import com.engine.order.infrastructure.security.JwtTokenProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

@RestController
@RequestMapping(path = "/api/v1/auth", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Authentication & Token Issuance", description = "Generates signed JWT tokens with Role-Based Access Control (CUSTOMER or ADMIN)")
public class AuthRestController {

    private final JwtTokenProvider jwtTokenProvider;

    public AuthRestController(JwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = Objects.requireNonNull(jwtTokenProvider, "jwtTokenProvider must not be null");
    }

    @PostMapping("/token")
    @Operation(summary = "Generate JWT Token", description = "Issues a signed HMAC-SHA256 Bearer token for the given user identity and role.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Token issued successfully",
                    content = @Content(schema = @Schema(implementation = AuthResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request payload")
    })
    public ResponseEntity<AuthResponse> issueToken(@Valid @RequestBody AuthRequest request) {
        String token = jwtTokenProvider.generateToken(request.username(), request.role());
        AuthResponse response = new AuthResponse(
                token,
                "Bearer",
                request.username(),
                request.role().toUpperCase(),
                86400L
        );
        return ResponseEntity.ok(response);
    }
}
