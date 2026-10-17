package com.jobtrack.web;

import com.jobtrack.dto.common.PageResponse;
import com.jobtrack.dto.reminder.ReminderRequest;
import com.jobtrack.dto.reminder.ReminderResponse;
import com.jobtrack.security.AuthenticatedUser;
import com.jobtrack.service.ReminderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

/** Reminders: the in-app "what do I need to do next" queue. */
@RestController
@RequestMapping("/api/reminders")
@RequiredArgsConstructor
@Validated
@Tag(name = "Reminders", description = "Deadlines, follow-ups and interview preparation")
public class ReminderController {

    private final ReminderService reminderService;

    @GetMapping
    @Operation(summary = "List reminders",
            description = "`scope` is one of OPEN (default), OVERDUE, UPCOMING, COMPLETED, ALL.")
    public PageResponse<ReminderResponse> list(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @RequestParam(defaultValue = "OPEN") String scope,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {

        return reminderService.list(currentUser, ReminderService.Scope.from(scope), page, size);
    }

    @GetMapping("/upcoming")
    @Operation(summary = "Reminders due soon", description = "Includes overdue items; dashboard widget.")
    public List<ReminderResponse> upcoming(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @RequestParam(defaultValue = "7") @Min(1) @Max(90) int days,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int limit) {

        return reminderService.upcoming(currentUser, days, limit);
    }

    @PostMapping
    @Operation(summary = "Create a manual reminder",
            description = "`applicationId` is optional; when present it must belong to the caller "
                    + "(otherwise 404). Returns 201.")
    public ResponseEntity<ReminderResponse> create(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @Valid @RequestBody ReminderRequest request) {

        ReminderResponse created = reminderService.create(currentUser, request);
        return ResponseEntity.created(URI.create("/api/reminders/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a reminder")
    public ReminderResponse update(@AuthenticationPrincipal AuthenticatedUser currentUser,
                                   @PathVariable Long id,
                                   @Valid @RequestBody ReminderRequest request) {
        return reminderService.update(currentUser, id, request);
    }

    @PatchMapping("/{id}/complete")
    @Operation(summary = "Mark a reminder completed",
            description = "Idempotent: completing twice keeps the original completion timestamp.")
    public ReminderResponse complete(@AuthenticationPrincipal AuthenticatedUser currentUser,
                                     @PathVariable Long id) {
        return reminderService.complete(currentUser, id);
    }

    @PatchMapping("/{id}/reopen")
    @Operation(summary = "Re-open a completed reminder")
    public ReminderResponse reopen(@AuthenticationPrincipal AuthenticatedUser currentUser,
                                   @PathVariable Long id) {
        return reminderService.reopen(currentUser, id);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a reminder", description = "Returns 204.")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthenticatedUser currentUser,
                                       @PathVariable Long id) {
        reminderService.delete(currentUser, id);
        return ResponseEntity.noContent().build();
    }
}
