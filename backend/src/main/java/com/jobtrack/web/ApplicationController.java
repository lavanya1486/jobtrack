package com.jobtrack.web;

import com.jobtrack.domain.ApplicationStatus;
import com.jobtrack.domain.EmploymentType;
import com.jobtrack.domain.WorkMode;
import com.jobtrack.dto.application.ApplicationDetailResponse;
import com.jobtrack.dto.application.ApplicationQuery;
import com.jobtrack.dto.application.ApplicationRequest;
import com.jobtrack.dto.application.ApplicationResponse;
import com.jobtrack.dto.application.FilterOptionsResponse;
import com.jobtrack.dto.application.StatusHistoryResponse;
import com.jobtrack.dto.application.StatusUpdateRequest;
import com.jobtrack.dto.common.PageResponse;
import com.jobtrack.exception.BadRequestException;
import com.jobtrack.security.AuthenticatedUser;
import com.jobtrack.service.ApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
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
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Job application CRUD, search and filtering.
 *
 * <p>Controllers stay thin: they translate HTTP into {@link ApplicationService} calls and never touch
 * entities. The user id always comes from the authenticated principal, never from the request, which
 * is what makes horizontal privilege escalation (IDOR) impossible here.</p>
 */
@RestController
@RequestMapping("/api/applications")
@RequiredArgsConstructor
@Validated
@Tag(name = "Applications", description = "Track job applications end to end")
public class ApplicationController {

    private final ApplicationService applicationService;

    @GetMapping
    @Operation(summary = "List, search, filter and paginate applications",
            description = """
                    All filters are combined with AND and executed server-side as one paged query.

                    * `search` matches company, job title and location (case-insensitive, wildcards escaped).
                    * `status`, `employmentType` and `workMode` accept repeated values: `?status=APPLIED&status=INTERVIEW`.
                    * `appliedFrom`/`appliedTo` and `deadlineFrom`/`deadlineTo` are inclusive `yyyy-MM-dd` bounds.
                    * `sort` accepts `newest`, `oldest`, `applied_date`, `deadline`, `company`, `title`,
                      `status`, `salary`, `updated`; prefix with `-` to reverse the direction.
                    * `page` is zero-based; `size` is capped at 100.
                    """)
    public PageResponse<ApplicationResponse> list(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @Parameter(description = "Free text over company, title and location")
            @RequestParam(required = false) String search,
            @RequestParam(required = false) List<ApplicationStatus> status,
            @RequestParam(required = false) List<EmploymentType> employmentType,
            @RequestParam(required = false) List<WorkMode> workMode,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate appliedFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate appliedTo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate deadlineFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate deadlineTo,
            @RequestParam(required = false) Boolean overdueOnly,
            @RequestParam(required = false) Boolean submittedOnly,
            @RequestParam(defaultValue = "newest") String sort,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "page must be >= 0") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "size must be >= 1")
            @Max(value = 100, message = "size must be <= 100") int size) {

        ApplicationQuery query = new ApplicationQuery(
                currentUser.id(),
                search,
                status,
                employmentType,
                workMode,
                location,
                tag,
                appliedFrom,
                appliedTo,
                deadlineFrom,
                deadlineTo,
                overdueOnly,
                submittedOnly,
                resolveSort(sort),
                page,
                size);

        return applicationService.list(query);
    }

    @PostMapping
    @Operation(summary = "Create an application",
            description = "Returns 201 with a Location header. A submitted status defaults appliedAt to today.")
    public ResponseEntity<ApplicationResponse> create(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @Valid @RequestBody ApplicationRequest request) {

        ApplicationResponse created = applicationService.create(currentUser, request);
        URI location = UriComponentsBuilder.fromPath("/api/applications/{id}")
                .buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping("/{id}")
    @Operation(summary = "View one application",
            description = "Returns the application plus its interview rounds and status timeline. "
                    + "Another user's id answers 404, never 403, so ids cannot be probed.")
    public ApplicationDetailResponse get(@AuthenticationPrincipal AuthenticatedUser currentUser,
                                         @PathVariable Long id) {
        return applicationService.getDetail(currentUser, id);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update an application",
            description = "Full update. Changing the status in the payload goes through the same "
                    + "transition policy as PATCH /status and is written to the audit trail.")
    public ApplicationResponse update(@AuthenticationPrincipal AuthenticatedUser currentUser,
                                      @PathVariable Long id,
                                      @Valid @RequestBody ApplicationRequest request) {
        return applicationService.update(currentUser, id, request);
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Change the status",
            description = "Validated against the transition policy: an illegal move returns 400 with "
                    + "the allowed targets in `details.allowedTargets`.")
    public ApplicationResponse changeStatus(@AuthenticationPrincipal AuthenticatedUser currentUser,
                                            @PathVariable Long id,
                                            @Valid @RequestBody StatusUpdateRequest request) {
        return applicationService.changeStatus(currentUser, id, request);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete an application",
            description = "Cascades to interviews, reminders, tags and status history. Returns 204.")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthenticatedUser currentUser,
                                       @PathVariable Long id) {
        applicationService.delete(currentUser, id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/status-history")
    @Operation(summary = "Status timeline of one application")
    public List<StatusHistoryResponse> history(@AuthenticationPrincipal AuthenticatedUser currentUser,
                                               @PathVariable Long id) {
        return applicationService.history(currentUser, id);
    }

    @GetMapping("/filter-options")
    @Operation(summary = "Values available for the filter bar",
            description = "Enums plus the distinct tags, locations and companies of the caller's own data.")
    public FilterOptionsResponse filterOptions(@AuthenticationPrincipal AuthenticatedUser currentUser) {
        return applicationService.filterOptions(currentUser);
    }

    /** Whitelists the sort key so an unknown property cannot reach the JPA criteria builder. */
    private ApplicationQuery.SortKey resolveSort(String sort) {
        try {
            return ApplicationQuery.SortField.from(sort);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage(),
                    Map.of("parameter", "sort",
                            "acceptedValues", ApplicationQuery.SortField.validKeys()));
        }
    }
}
