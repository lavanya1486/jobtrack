package com.jobtrack.service;

import com.jobtrack.domain.ApplicationStatus;
import com.jobtrack.dto.auth.ChangePasswordRequest;
import com.jobtrack.dto.auth.UpdateProfileRequest;
import com.jobtrack.dto.auth.UserResponse;
import com.jobtrack.exception.BadRequestException;
import com.jobtrack.exception.NotFoundException;
import com.jobtrack.exception.UnauthorizedException;
import com.jobtrack.domain.User;
import com.jobtrack.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.Map;

/** Profile management for the authenticated user. */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;

    @Transactional(readOnly = true)
    public UserResponse getProfile(Long userId) {
        return AuthService.toUserResponse(load(userId));
    }

    /**
     * Updates name, time zone and the e-mail reminder opt-in.
     *
     * <p>The time zone is validated against the IANA database because reminder times are rendered
     * with it: an invalid id would silently shift every due date in the UI.</p>
     */
    @Transactional
    public UserResponse updateProfile(Long userId, UpdateProfileRequest request) {
        User user = load(userId);
        user.setName(request.name().trim());

        if (StringUtils.hasText(request.timezone())) {
            String zone = request.timezone().trim();
            try {
                ZoneId.of(zone);
            } catch (DateTimeException e) {
                throw new BadRequestException("Unknown time zone '" + zone + "'",
                        Map.of("parameter", "timezone", "hint",
                                "Use an IANA zone id such as Asia/Kolkata, Europe/London or UTC"));
            }
            user.setTimezone(zone);
        }
        if (request.emailRemindersEnabled() != null) {
            user.setEmailRemindersEnabled(request.emailRemindersEnabled());
        }
        userRepository.save(user);
        return AuthService.toUserResponse(user);
    }

    /**
     * Changes the password and revokes every refresh token, so other devices are signed out and a
     * stolen session cannot survive a password change.
     */
    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = load(userId);
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new UnauthorizedException("Current password is incorrect",
                    Map.of("reason", "invalid_credentials", "parameter", "currentPassword"));
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new BadRequestException("New password must be different from the current password");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);

        int revoked = refreshTokenService.revokeAllForUser(userId);
        log.info("Password changed for user id={}, revoked {} refresh tokens", userId, revoked);
    }

    private User load(Long userId) {
        return userRepository.findById(userId).orElseThrow(() -> NotFoundException.user(userId));
    }
}
