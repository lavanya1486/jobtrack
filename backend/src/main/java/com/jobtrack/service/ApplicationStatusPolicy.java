package com.jobtrack.service;

import com.jobtrack.domain.ApplicationStatus;
import com.jobtrack.exception.BadRequestException;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Legal status transitions for a job application.
 *
 * <p>Why a policy at all: free-form status editing produces analytics nobody can trust (an
 * application that jumps {@code SAVED -> OFFER} inflates the offer rate without ever being
 * submitted). The matrix below keeps the funnel monotonic while still allowing genuine
 * corrections:</p>
 *
 * <ul>
 *   <li>A draft must be submitted before it can progress.</li>
 *   <li>In-progress applications move forward, or close as rejected / withdrawn.</li>
 *   <li>A closed application can be re-opened, which is recorded in the audit trail.</li>
 *   <li>Creating an application <em>directly</em> in any status is allowed - candidates adopt the
 *       tool mid-process. The policy only governs transitions of existing records.</li>
 * </ul>
 */
public final class ApplicationStatusPolicy {

    private static final Map<ApplicationStatus, Set<ApplicationStatus>> ALLOWED = new LinkedHashMap<>();

    static {
        ALLOWED.put(ApplicationStatus.SAVED,
                EnumSet.of(ApplicationStatus.APPLIED, ApplicationStatus.WITHDRAWN));
        ALLOWED.put(ApplicationStatus.APPLIED,
                EnumSet.of(ApplicationStatus.ASSESSMENT, ApplicationStatus.INTERVIEW,
                        ApplicationStatus.OFFER, ApplicationStatus.REJECTED,
                        ApplicationStatus.WITHDRAWN));
        ALLOWED.put(ApplicationStatus.ASSESSMENT,
                EnumSet.of(ApplicationStatus.APPLIED, ApplicationStatus.INTERVIEW,
                        ApplicationStatus.OFFER, ApplicationStatus.REJECTED,
                        ApplicationStatus.WITHDRAWN));
        ALLOWED.put(ApplicationStatus.INTERVIEW,
                EnumSet.of(ApplicationStatus.ASSESSMENT, ApplicationStatus.OFFER,
                        ApplicationStatus.REJECTED, ApplicationStatus.WITHDRAWN));
        ALLOWED.put(ApplicationStatus.OFFER,
                EnumSet.of(ApplicationStatus.REJECTED, ApplicationStatus.WITHDRAWN));
        ALLOWED.put(ApplicationStatus.REJECTED,
                EnumSet.of(ApplicationStatus.APPLIED, ApplicationStatus.ASSESSMENT,
                        ApplicationStatus.INTERVIEW, ApplicationStatus.OFFER,
                        ApplicationStatus.WITHDRAWN));
        ALLOWED.put(ApplicationStatus.WITHDRAWN,
                EnumSet.of(ApplicationStatus.APPLIED, ApplicationStatus.ASSESSMENT,
                        ApplicationStatus.INTERVIEW, ApplicationStatus.OFFER,
                        ApplicationStatus.REJECTED));
    }

    private ApplicationStatusPolicy() {
    }

    public static boolean isAllowed(ApplicationStatus from, ApplicationStatus to) {
        if (from == null || to == null) {
            return false;
        }
        return ALLOWED.getOrDefault(from, Set.of()).contains(to);
    }

    public static Set<ApplicationStatus> allowedTargets(ApplicationStatus from) {
        return ALLOWED.getOrDefault(from, Set.of());
    }

    /**
     * @throws BadRequestException when the transition is a no-op or not permitted; the error carries
     *                             the allowed targets so the UI can render them.
     */
    public static void validate(ApplicationStatus from, ApplicationStatus to) {
        if (from == to) {
            throw new BadRequestException("Application is already in status " + from);
        }
        if (!isAllowed(from, to)) {
            throw new BadRequestException(
                    "Cannot change status from " + from + " to " + to,
                    Map.of(
                            "from", from.name(),
                            "to", to.name(),
                            "allowedTargets", allowedTargets(from).stream().map(Enum::name).toList()));
        }
    }
}
