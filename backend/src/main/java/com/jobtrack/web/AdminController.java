package com.jobtrack.web;

import com.jobtrack.dto.admin.AdminStatsResponse;
import com.jobtrack.dto.admin.AdminUserResponse;
import com.jobtrack.dto.admin.RoleUpdateRequest;
import com.jobtrack.security.AuthenticatedUser;
import com.jobtrack.service.AdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Administration endpoints.
 *
 * <p>Requires the {@code ADMIN} role: enforced by the URL rule in {@code SecurityConfig}, by
 * {@code @PreAuthorize} on {@link AdminService} and again here, so the contract survives a
 * copy-paste of any single layer. An ordinary {@code USER} receives {@code 403 Forbidden}.</p>
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin", description = "ADMIN only: accounts and platform statistics")
public class AdminController {

    private final AdminService adminService;

    @GetMapping("/users")
    @Operation(summary = "List all accounts with activity counters")
    public List<AdminUserResponse> listUsers() {
        return adminService.listUsers();
    }

    @GetMapping("/stats")
    @Operation(summary = "Platform statistics")
    public AdminStatsResponse stats() {
        return adminService.stats();
    }

    @PatchMapping("/users/{id}/role")
    @Operation(summary = "Change a user's role",
            description = "409 when it would remove the last administrator, 400 when changing your own "
                    + "role. All of the target user's refresh tokens are revoked so the change applies "
                    + "to their active sessions.")
    public AdminUserResponse changeRole(@AuthenticationPrincipal AuthenticatedUser currentUser,
                                        @PathVariable Long id,
                                        @Valid @RequestBody RoleUpdateRequest request) {
        return adminService.changeRole(currentUser.id(), id, request.role());
    }

    @DeleteMapping("/users/{id}")
    @Operation(summary = "Delete an account and all of its data", description = "Returns 204.")
    public ResponseEntity<Void> deleteUser(@AuthenticationPrincipal AuthenticatedUser currentUser,
                                           @PathVariable Long id) {
        adminService.deleteUser(currentUser.id(), id);
        return ResponseEntity.noContent().build();
    }
}
