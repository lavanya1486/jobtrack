package com.jobtrack.web;

import com.jobtrack.dto.interview.InterviewRequest;
import com.jobtrack.dto.interview.InterviewResponse;
import com.jobtrack.security.AuthenticatedUser;
import com.jobtrack.service.InterviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

/**
 * Interview rounds of one application (the endpoints named in the API specification).
 *
 * <p>Kept in its own controller so the URL shape stays exactly
 * {@code /api/applications/{id}/interviews} while the cross-application views live under
 * {@code /api/interviews}.</p>
 */
@RestController
@RequestMapping("/api/applications/{applicationId}/interviews")
@RequiredArgsConstructor
@Tag(name = "Interviews", description = "Rounds of a single application")
public class ApplicationInterviewController {

    private final InterviewService interviewService;

    @GetMapping
    @Operation(summary = "List interview rounds of an application", description = "Chronological order.")
    public List<InterviewResponse> list(@AuthenticationPrincipal AuthenticatedUser currentUser,
                                        @PathVariable Long applicationId) {
        return interviewService.listForApplication(currentUser, applicationId);
    }

    @PostMapping
    @Operation(summary = "Schedule an interview round",
            description = """
                    Returns 201. Side effects that keep the data consistent:
                    * an `APPLIED` or `ASSESSMENT` application is advanced to `INTERVIEW` and the move is
                      written to the status history,
                    * `firstResponseAt` is stamped if it was not set,
                    * a preparation reminder is created `app.reminders.interview-prep-lead-time` before the round.

                    Scheduling a round for a `SAVED` draft returns 400: submit the application first.
                    """)
    public ResponseEntity<InterviewResponse> create(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long applicationId,
            @Valid @RequestBody InterviewRequest request) {

        InterviewResponse created = interviewService.create(currentUser, applicationId, request);
        URI location = URI.create("/api/applications/" + applicationId + "/interviews/" + created.id());
        return ResponseEntity.created(location).body(created);
    }
}
