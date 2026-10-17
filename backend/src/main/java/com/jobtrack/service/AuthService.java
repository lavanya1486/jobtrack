package com.jobtrack.service;

import com.jobtrack.domain.Role;
import com.jobtrack.domain.User;
import com.jobtrack.dto.auth.AuthResponse;
import com.jobtrack.dto.auth.LoginRequest;
import com.jobtrack.dto.auth.RefreshRequest;
import com.jobtrack.dto.auth.RegisterRequest;
import com.jobtrack.dto.auth.UserResponse;
import com.jobtrack.exception.ConflictException;
import com.jobtrack.exception.UnauthorizedException;
import com.jobtrack.repository.UserRepository;
import com.jobtrack.security.JwtService;
import com.jobtrack.security.RequestMetadata;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Map;

/**
 * Registration, login, token refresh and logout.
 *
 * <p>Security notes:</p>
 * <ul>
 *   <li>Passwords are hashed with BCrypt before persistence; the plain value is never logged or
 *       stored, and the entity field is named {@code passwordHash} to make that obvious.</li>
 *   <li>Login failures return the same message for unknown e-mail, disabled account and wrong
 *       password, so the endpoint cannot enumerate registered users.</li>
 *   <li>Role is never taken from the request: everybody registers as {@code USER}. Admins are
 *       promoted through the seeded account or the {@code /api/admin} endpoints.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    @Transactional
    public AuthResponse register(RegisterRequest request, RequestMetadata metadata) {
        String email = normalise(request.email());
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("An account with this e-mail already exists");
        }

        User user = User.of(request.name().trim(), email,
                passwordEncoder.encode(request.password()), Role.USER);
        userRepository.save(user);
        log.info("Registered user id={} email={}", user.getId(), email);

        return tokensFor(user, metadata);
    }

    @Transactional
    public AuthResponse login(LoginRequest request, RequestMetadata metadata) {
        User user = userRepository.findByEmailIgnoreCase(normalise(request.email()))
                .orElseThrow(() -> invalidCredentials());

        if (!user.isEnabled() || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            // Same exception for both cases on purpose.
            throw invalidCredentials();
        }
        return tokensFor(user, metadata);
    }

    /**
     * Deliberately <em>not</em> {@code @Transactional}: the rotation must be its own transaction so
     * that the revocation written during reuse detection is committed even though a 401 is thrown
     * right afterwards. An outer transaction here would roll that security-critical write back.
     */
    public AuthResponse refresh(RefreshRequest request, RequestMetadata metadata) {
        RefreshTokenService.RotationResult rotation =
                refreshTokenService.rotate(request.refreshToken(), metadata);
        return tokensFor(rotation.user(), metadata, rotation.refreshToken());
    }

    /** Logout is idempotent and never fails on an unknown token. */
    @Transactional
    public void logout(String refreshToken) {
        refreshTokenService.revoke(refreshToken);
    }

    private AuthResponse tokensFor(User user, RequestMetadata metadata) {
        return tokensFor(user, metadata, refreshTokenService.issue(user, metadata));
    }

    private AuthResponse tokensFor(User user, RequestMetadata metadata, String refreshToken) {
        String accessToken = jwtService.generateAccessToken(
                user.getId(), user.getEmail(), user.getName(), user.getRole().name());
        return AuthResponse.of(accessToken, refreshToken,
                jwtService.accessTokenTtl().toSeconds(), toUserResponse(user));
    }

    public static UserResponse toUserResponse(User user) {
        return new UserResponse(user.getId(), user.getName(), user.getEmail(), user.getRole(),
                user.getTimezone(), user.isEmailRemindersEnabled(), user.getCreatedAt());
    }

    private static UnauthorizedException invalidCredentials() {
        return new UnauthorizedException("Invalid e-mail or password",
                Map.of("reason", "invalid_credentials"));
    }

    private static String normalise(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
