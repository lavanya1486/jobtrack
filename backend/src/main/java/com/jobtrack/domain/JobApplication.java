package com.jobtrack.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * One job application owned by exactly one {@link User}.
 *
 * <p>Ownership is enforced in the service layer and by repository queries that always carry the
 * authenticated user id - never by hiding rows in the UI.</p>
 */
@Entity
@Table(name = "job_applications", indexes = {
        @Index(name = "ix_applications_user_status", columnList = "user_id, status")
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class JobApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Denormalised owner id: keeps every ownership check a simple indexed predicate. */
    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(nullable = false, length = 140)
    private String company;

    @Column(name = "job_title", nullable = false, length = 160)
    private String jobTitle;

    @Column(name = "job_description", columnDefinition = "text")
    private String jobDescription;

    @Column(name = "job_url", length = 500)
    private String jobUrl;

    /** Where the posting was found: LinkedIn, company career page, referral, ... */
    @Column(length = 60)
    private String source;

    @Enumerated(EnumType.STRING)
    @Column(name = "employment_type", nullable = false, length = 24)
    private EmploymentType employmentType = EmploymentType.FULL_TIME;

    @Enumerated(EnumType.STRING)
    @Column(name = "work_mode", nullable = false, length = 16)
    private WorkMode workMode = WorkMode.ONSITE;

    @Column(length = 160)
    private String location;

    @Column(nullable = false, length = 8)
    private String currency = "INR";

    @Column(name = "expected_salary_min", precision = 14, scale = 2)
    private BigDecimal expectedSalaryMin;

    @Column(name = "expected_salary_max", precision = 14, scale = 2)
    private BigDecimal expectedSalaryMax;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ApplicationStatus status = ApplicationStatus.SAVED;

    @Column(name = "applied_at")
    private LocalDate appliedAt;

    @Column(name = "deadline")
    private LocalDate deadline;

    @Column(name = "next_action_date")
    private LocalDate nextActionDate;

    @Column(name = "next_action", length = 200)
    private String nextAction;

    @Column(columnDefinition = "text")
    private String notes;

    /**
     * Moment the employer first reacted (moved the application forward or rejected it). Set by the
     * service layer; used as the numerator of the response rate.
     */
    @Column(name = "first_response_at")
    private Instant firstResponseAt;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "application_tags", joinColumns = @JoinColumn(name = "application_id"))
    @Column(name = "tag", nullable = false, length = 40)
    private Set<String> tags = new LinkedHashSet<>();

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public JobApplication(Long userId, String company, String jobTitle) {
        this.userId = userId;
        this.company = company;
        this.jobTitle = jobTitle;
    }

    /** True when the posting deadline has passed and the process is still open. */
    public boolean isDeadlineOverdue(LocalDate today) {
        return deadline != null && deadline.isBefore(today) && !status.isClosed();
    }

    public boolean hasRecordedEmployerResponse() {
        return firstResponseAt != null;
    }
}
