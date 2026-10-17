package com.jobtrack.web;

import com.jobtrack.dto.auth.ChangePasswordRequest;
import com.jobtrack.dto.auth.UpdateProfileRequest;
import com.jobtrack.dto.auth.UserResponse;
import com.jobtrack.security.AuthenticatedUser;
import com.jobtrack.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Profile and session settings of the authenticated user. */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "Profile", description = "Current user settings")
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    @Operation(summary = "Current profile", description = "Also the cheapest way to validate an access token.")
    public UserResponse me(@AuthenticationPrincipal AuthenticatedUser currentUser) {
        return userService.getProfile(currentUser.id());
    }

    @PatchMapping("/me")
    @Operation(summary = "Update profile",
            description = "Name, IANA time zone and the e-mail reminder opt-in. An unknown time zone "
                    + "returns 400 because every reminder time is rendered with it.")
    public UserResponse update(@AuthenticationPrincipal AuthenticatedUser currentUser,
                               @Valid @RequestBody UpdateProfileRequest request) {
        return userService.updateProfile(currentUser.id(), request);
    }

    @PostMapping("/me/password")
    @Operation(summary = "Change password",
            description = "Requires the current password and revokes every refresh token, signing out "
                    + "other devices. Returns 204.")
    public ResponseEntity<Void> changePassword(@AuthenticationPrincipal AuthenticatedUser currentUser,
                                               @Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(currentUser.id(), request);
        return ResponseEntity.noContent().build();
    }
}
