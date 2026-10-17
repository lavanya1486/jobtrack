package com.jobtrack.service;

import com.jobtrack.domain.Role;
import com.jobtrack.domain.User;
import com.jobtrack.dto.admin.AdminStatsResponse;
import com.jobtrack.dto.admin.AdminUserResponse;
import com.jobtrack.exception.BadRequestException;
import com.jobtrack.exception.ConflictException;
import com.jobtrack.exception.NotFoundException;
import com.jobtrack.repository.InterviewRepository;
import com.jobtrack.repository.JobApplicationRepository;
import com.jobtrack.repository.RefreshTokenRepository;
import com.jobtrack.repository.ReminderRepository;
import com.jobtrack.repository.UserRepository;
import com.jobtrack.domain.ApplicationStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * ADMIN-only operations.
 *
 * <p>Protected twice on purpose: the URL rule in {@code SecurityConfig} and
 * {@code @PreAuthorize} here, so moving a method to another controller cannot silently expose it.
 * The guards also prevent an admin from locking everybody (or themselves) out.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('ADMIN')")
public class AdminService {

    private final UserRepository userRepository;
    private final JobApplicationRepository applicationRepository;
    private final InterviewRepository interviewRepository;
    private final ReminderRepository reminderRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final RefreshTokenService refreshTokenService;

    @Transactional(readOnly = true)
    public List<AdminUserResponse> listUsers() {
        return userRepository.findAllWithActivity().stream()
                .map(row -> new AdminUserResponse(row.id(), row.name(), row.email(), row.role(),
                        row.enabled(), row.timezone(), row.applications(), row.interviews(),
                        row.openReminders(), row.createdAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public AdminStatsResponse stats() {
        long totalUsers = userRepository.count();
        long submitted = applicationRepository.countByStatusNot(ApplicationStatus.SAVED);
        return new AdminStatsResponse(
                totalUsers,
                userRepository.countByEnabledTrue(),
                userRepository.countAdmins(),
                applicationRepository.count(),
                submitted,
                interviewRepository.count(),
                reminderRepository.countAllOpen(),
                refreshTokenRepository.countByRevokedAtIsNullAndExpiresAtAfter(Instant.now()),
                totalUsers == 0 ? 0d
                        : Math.round((double) applicationRepository.count() / totalUsers * 100) / 100d);
    }

    /**
     * Promotes or demotes a user.
     *
     * @throws ConflictException when the change would remove the last administrator
     * @throws BadRequestException when an admin tries to change their own role
     */
    @Transactional
    public AdminUserResponse changeRole(Long actingAdminId, Long targetUserId, Role role) {
        if (actingAdminId.equals(targetUserId)) {
            throw new BadRequestException("You cannot change your own role");
        }
        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> NotFoundException.user(targetUserId));

        if (target.getRole() == Role.ADMIN && role != Role.ADMIN && userRepository.countAdmins() <= 1) {
            throw new ConflictException("Cannot demote the last remaining administrator");
        }
        if (target.getRole() == role) {
            throw new BadRequestException("User already has role " + role);
        }

        target.setRole(role);
        userRepository.save(target);

        // A role change must take effect on other devices too.
        refreshTokenService.revokeAllForUser(targetUserId);
        log.warn("Admin {} changed role of user {} to {}", actingAdminId, targetUserId, role);

        return new AdminUserResponse(target.getId(), target.getName(), target.getEmail(),
                target.getRole(), target.isEnabled(), target.getTimezone(),
                applicationRepository.countByUserId(targetUserId),
                interviewRepository.countForUser(targetUserId),
                reminderRepository.countOpen(targetUserId),
                target.getCreatedAt());
    }

    /**
     * Deletes an account and, through database cascades, all of its applications, interviews,
     * reminders, audit rows and refresh tokens.
     */
    @Transactional
    public void deleteUser(Long actingAdminId, Long targetUserId) {
        if (actingAdminId.equals(targetUserId)) {
            throw new BadRequestException("You cannot delete your own account from the admin console");
        }
        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> NotFoundException.user(targetUserId));
        if (target.getRole() == Role.ADMIN && userRepository.countAdmins() <= 1) {
            throw new ConflictException("Cannot delete the last remaining administrator");
        }
        userRepository.delete(target);
        log.warn("Admin {} deleted user {}", actingAdminId, targetUserId);
    }
}
