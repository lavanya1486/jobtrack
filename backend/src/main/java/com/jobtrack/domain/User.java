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
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * An account. Passwords are stored as BCrypt hashes only - the plain password never reaches this
 * entity (see {@code RegisterRequest} / {@code AuthService}).
 */
@Entity
@Table(name = "users")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(nullable = false, unique = true, length = 160)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Role role = Role.USER;

    /** IANA zone id (e.g. {@code Asia/Kolkata}) used to render reminder times for this user. */
    @Column(nullable = false, length = 64)
    private String timezone = "UTC";

    @Column(name = "email_reminders_enabled", nullable = false)
    private boolean emailRemindersEnabled = false;

    @Column(nullable = false)
    private boolean enabled = true;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static User of(String name, String email, String passwordHash, Role role) {
        User user = new User();
        user.name = name;
        user.email = email;
        user.passwordHash = passwordHash;
        user.role = role;
        return user;
    }

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }
}
