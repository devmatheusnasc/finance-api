package br.com.finance.modules.authentication.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "user_credential")
class CredentialJpaEntity {

    @Id
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "failed_attempt_count", nullable = false)
    private int failedAttemptCount;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "password_changed_at", nullable = false)
    private Instant passwordChangedAt;

    CredentialJpaEntity(Long userId, String passwordHash, int failedAttemptCount, Instant lockedUntil,
                        Instant passwordChangedAt) {

        this.userId = userId;
        this.passwordHash = passwordHash;
        this.failedAttemptCount = failedAttemptCount;
        this.lockedUntil = lockedUntil;
        this.passwordChangedAt = passwordChangedAt;
    }
}