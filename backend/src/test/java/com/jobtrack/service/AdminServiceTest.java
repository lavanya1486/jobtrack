package com.jobtrack.service;

import com.jobtrack.domain.Role;
import com.jobtrack.domain.User;
import com.jobtrack.exception.BadRequestException;
import com.jobtrack.exception.ConflictException;
import com.jobtrack.exception.NotFoundException;
import com.jobtrack.repository.InterviewRepository;
import com.jobtrack.repository.JobApplicationRepository;
import com.jobtrack.repository.RefreshTokenRepository;
import com.jobtrack.repository.ReminderRepository;
import com.jobtrack.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Guards inside {@link AdminService} that the HTTP layer cannot reach on its own.
 *
 * <p>An admin can only act on accounts while they hold the role, so "the last administrator" is
 * normally unreachable through the API (the actor would be that administrator, which trips the
 * self-service guard first). The rules still exist for concurrent demotions and for any future
 * caller, so they are pinned here.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AdminService guards")
class AdminServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private JobApplicationRepository applicationRepository;
    @Mock
    private InterviewRepository interviewRepository;
    @Mock
    private ReminderRepository reminderRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private RefreshTokenService refreshTokenService;

    @InjectMocks
    private AdminService adminService;

    private User user(long id, Role role) {
        User user = new User();
        user.setId(id);
        user.setRole(role);
        user.setEnabled(true);
        return user;
    }

    @Test
    @DisplayName("the last remaining administrator cannot be demoted")
    void refusesToDemoteTheLastAdministrator() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(user(2L, Role.ADMIN)));
        when(userRepository.countAdmins()).thenReturn(1L);

        assertThatThrownBy(() -> adminService.changeRole(1L, 2L, Role.USER))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("last remaining administrator");

        verify(userRepository, never()).save(any());
        verifyNoInteractions(refreshTokenService);
    }

    @Test
    @DisplayName("the last remaining administrator cannot be deleted")
    void refusesToDeleteTheLastAdministrator() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(user(2L, Role.ADMIN)));
        when(userRepository.countAdmins()).thenReturn(1L);

        assertThatThrownBy(() -> adminService.deleteUser(1L, 2L))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("last remaining administrator");

        verify(userRepository, never()).delete(any());
    }

    @Test
    @DisplayName("an admin cannot demote or delete their own account")
    void refusesSelfService() {
        assertThatThrownBy(() -> adminService.changeRole(7L, 7L, Role.USER))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("your own role");
        assertThatThrownBy(() -> adminService.deleteUser(7L, 7L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("your own account");

        verifyNoInteractions(userRepository);
    }

    @Test
    @DisplayName("unknown accounts are reported as not found, not as a permission problem")
    void unknownAccountsAreNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminService.changeRole(1L, 99L, Role.ADMIN))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> adminService.deleteUser(1L, 99L))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("setting a role a user already holds is rejected")
    void refusesANoopRoleChange() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(user(2L, Role.USER)));

        assertThatThrownBy(() -> adminService.changeRole(1L, 2L, Role.USER))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("already has role");

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("a role change is persisted and signs the target's other sessions out")
    void roleChangeRevokesTheTargetsSessions() {
        User target = user(2L, Role.USER);
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));

        var updated = adminService.changeRole(1L, 2L, Role.ADMIN);

        assertThat(updated.role()).isEqualTo(Role.ADMIN);
        assertThat(target.getRole()).isEqualTo(Role.ADMIN);
        verify(userRepository).save(target);
        verify(refreshTokenService).revokeAllForUser(2L);
    }

    @Test
    @DisplayName("a second administrator can be removed while another one remains")
    void deletesAnAdminWhenAnotherRemains() {
        User target = user(2L, Role.ADMIN);
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));
        when(userRepository.countAdmins()).thenReturn(2L);

        adminService.deleteUser(1L, 2L);

        verify(userRepository).delete(target);
    }
}
