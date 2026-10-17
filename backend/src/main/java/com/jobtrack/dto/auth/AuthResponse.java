package com.jobtrack.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Result of a successful login or token refresh.
 *
 * @param accessToken   short-lived JWT (default 30 min) sent as {@code Authorization: Bearer <token>}
 * @param refreshToken  opaque, long-lived token (default 7 days) used once to rotate
 * @param tokenType     always {@code Bearer}
 * @param expiresIn     access-token lifetime in seconds
 * @param user          the authenticated profile
 */
@Schema(description = "Access + refresh token pair")
public record AuthResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        UserResponse user
) {

    public static AuthResponse of(String accessToken, String refreshToken, long expiresIn, UserResponse user) {
        return new AuthResponse(accessToken, refreshToken, "Bearer", expiresIn, user);
    }
}
