package com.jobtrack.web;

import com.jobtrack.dto.common.PageResponse;
import com.jobtrack.dto.interview.InterviewRequest;
import com.jobtrack.dto.interview.InterviewResponse;
import com.jobtrack.security.AuthenticatedUser;
import com.jobtrack.service.InterviewService;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Interview rounds across all of the caller's applications.
 *
 * <p>Round-scoped routes live under {@code /api/applications/{id}/interviews} (see
 * {@code ApplicationInterviewController}); this controller serves the cross-application views used by
 * the Interviews page and the dashboard.</p>
 */
@RestController
@RequestMapping("/api/interviews")
@RequiredArgsConstructor
@Validated
@Tag(name = "Interviews", description = "Schedule and review interview rounds")
public class InterviewController {

    private final InterviewService interviewService;

    @GetMapping
    @Operation(summary = "List interview rounds",
            description = "`scope=UPCOMING` (default) returns rounds that have not happened yet, "
                    + "`PAST` the ones already scheduled in the past, `ALL` everything.")
    public PageResponse<InterviewResponse> list(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @RequestParam(defaultValue = "UPCOMING") String scope,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {

        return interviewService.list(currentUser, InterviewService.InterviewScope.from(scope), page, size);
    }

    @GetMapping("/upcoming")
    @Operation(summary = "Next interview rounds", description = "Soonest first; used by the dashboard.")
    public List<InterviewResponse> upcoming(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @RequestParam(defaultValue = "5") @Min(1) @Max(25) int limit) {

        return interviewService.upcoming(currentUser, limit);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update or reschedule a round",
            description = "Also accepts the outcome and feedback after the interview happened.")
    public InterviewResponse update(@AuthenticationPrincipal AuthenticatedUser currentUser,
                                    @PathVariable Long id,
                                    @Valid @RequestBody InterviewRequest request) {
        return interviewService.update(currentUser, id, request);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Cancel a round", description = "Removes the round and its prep reminder. Returns 204.")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthenticatedUser currentUser,
                                       @PathVariable Long id) {
        interviewService.delete(currentUser, id);
        return ResponseEntity.noContent().build();
    }
}
