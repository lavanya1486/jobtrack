package com.jobtrack.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Append-only audit row written whenever an application's status changes.
 *
 * <p>Used for the per-application timeline and for the conversion funnel (which counts stages an
 * application <em>reached</em>, not only where it currently sits).</p>
 */
@Entity
@Table(name = "application_status_history")
@Getter
@Setter
@NoArgsConstructor
public class ApplicationStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "application_id", nullable = false, updatable = false)
    private Long applicationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 20)
    private ApplicationStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 20)
    private ApplicationStatus toStatus;

    @Column(length = 400)
    private String note;

    @Column(name = "changed_by", updatable = false)
    private Long changedBy;

    @Column(name = "changed_at", nullable = false, updatable = false)
    private Instant changedAt = Instant.now();

    public static ApplicationStatusHistory of(Long applicationId, ApplicationStatus from,
                                              ApplicationStatus to, String note, Long changedBy) {
        ApplicationStatusHistory history = new ApplicationStatusHistory();
        history.applicationId = applicationId;
        history.fromStatus = from;
        history.toStatus = to;
        history.note = note;
        history.changedBy = changedBy;
        history.changedAt = Instant.now();
        return history;
    }
}
