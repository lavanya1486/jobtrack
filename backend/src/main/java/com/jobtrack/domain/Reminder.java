package com.jobtrack.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * A due item surfaced on the dashboard.
 *
 * <p>Auto-generated reminders carry a {@code dedupeKey}; a partial unique index in the schema
 * guarantees the scheduler cannot create the same open reminder twice, even if it runs on several
 * instances or is triggered manually.</p>
 */
@Entity
@Table(name = "reminders")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class Reminder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "application_id", updatable = false)
    private Long applicationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 28)
    private ReminderType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private ReminderSource source = ReminderSource.MANUAL;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 500)
    private String details;

    @Column(name = "due_at", nullable = false)
    private Instant dueAt;

    /** Stable identity for scheduler-generated reminders; {@code null} for manual ones. */
    @Column(name = "dedupe_key", length = 200)
    private String dedupeKey;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "notified_at")
    private Instant notifiedAt;

    @Column(name = "email_sent_at")
    private Instant emailSentAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public Reminder(Long userId, Long applicationId, ReminderType type, ReminderSource source,
                    String title, Instant dueAt) {
        this.userId = userId;
        this.applicationId = applicationId;
        this.type = type;
        this.source = source;
        this.title = title;
        this.dueAt = dueAt;
    }

    public boolean isCompleted() {
        return completedAt != null;
    }

    public boolean isOverdue(Instant now) {
        return !isCompleted() && dueAt.isBefore(now);
    }
}
