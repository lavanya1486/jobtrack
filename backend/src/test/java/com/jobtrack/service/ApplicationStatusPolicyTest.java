package com.jobtrack.service;

import com.jobtrack.domain.ApplicationStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the status transition matrix.
 *
 * <p>No Spring context, no database: the policy is a pure function, and it is the rule that keeps the
 * analytics trustworthy (a draft that could jump straight to OFFER would inflate the offer rate).</p>
 */
class ApplicationStatusPolicyTest {

    @ParameterizedTest(name = "{0} -> {1} is allowed")
    @CsvSource({
            "SAVED, APPLIED",
            "SAVED, WITHDRAWN",
            "APPLIED, ASSESSMENT",
            "APPLIED, INTERVIEW",
            "APPLIED, OFFER",
            "APPLIED, REJECTED",
            "ASSESSMENT, INTERVIEW",
            "INTERVIEW, OFFER",
            "INTERVIEW, REJECTED",
            "OFFER, WITHDRAWN",
            "REJECTED, APPLIED",
            "WITHDRAWN, INTERVIEW",
    })
    void allowsLegalProgression(ApplicationStatus from, ApplicationStatus to) {
        assertThat(ApplicationStatusPolicy.isAllowed(from, to)).isTrue();
    }

    @ParameterizedTest(name = "{0} -> {1} is rejected")
    @CsvSource({
            "SAVED, OFFER",
            "SAVED, INTERVIEW",
            "SAVED, ASSESSMENT",
            "SAVED, REJECTED",
            "OFFER, APPLIED",
            "OFFER, INTERVIEW",
            "INTERVIEW, APPLIED",
    })
    void rejectsIllegalProgression(ApplicationStatus from, ApplicationStatus to) {
        assertThat(ApplicationStatusPolicy.isAllowed(from, to)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(ApplicationStatus.class)
    void neverAllowsANoOpTransition(ApplicationStatus status) {
        assertThat(ApplicationStatusPolicy.isAllowed(status, status))
                .as("%s -> %s must be rejected as a no-op", status, status)
                .isFalse();
    }

    @ParameterizedTest
    @EnumSource(ApplicationStatus.class)
    void everyStatusHasAtLeastOneEscapeHatch(ApplicationStatus status) {
        assertThat(ApplicationStatusPolicy.allowedTargets(status)).isNotEmpty();
    }

    @Test
    @DisplayName("a draft must be submitted before it can enter the funnel")
    void draftsStayOutOfTheFunnel() {
        Set<ApplicationStatus> targets = ApplicationStatusPolicy.allowedTargets(ApplicationStatus.SAVED);

        assertThat(targets).containsExactlyInAnyOrder(ApplicationStatus.APPLIED, ApplicationStatus.WITHDRAWN);
        assertThat(targets).doesNotContain(
                ApplicationStatus.ASSESSMENT,
                ApplicationStatus.INTERVIEW,
                ApplicationStatus.OFFER);
    }

    @Test
    @DisplayName("a rejected application can be reopened - recruiters do come back")
    void rejectedApplicationsCanBeReopened() {
        assertThat(ApplicationStatusPolicy.allowedTargets(ApplicationStatus.REJECTED))
                .contains(ApplicationStatus.APPLIED, ApplicationStatus.INTERVIEW, ApplicationStatus.OFFER);
    }

    @Test
    void nullsAreRejectedInsteadOfThrowing() {
        assertThat(ApplicationStatusPolicy.isAllowed(null, ApplicationStatus.APPLIED)).isFalse();
        assertThat(ApplicationStatusPolicy.isAllowed(ApplicationStatus.APPLIED, null)).isFalse();
        assertThat(ApplicationStatusPolicy.allowedTargets(null)).isEmpty();
    }

    @Test
    @DisplayName("the matrix is complete: every status appears as a key")
    void matrixCoversEveryStatus() {
        for (ApplicationStatus status : ApplicationStatus.values()) {
            assertThat(ApplicationStatusPolicy.allowedTargets(status))
                    .as("targets for %s", status)
                    .isNotNull()
                    .allSatisfy(target -> assertThat(target).isInstanceOf(ApplicationStatus.class));
        }
    }
}
