package br.com.finance.modules.authentication.domain.model;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

@Getter
public final class Credential {

    private final Long userId;
    private final String passwordHash;
    private final Instant passwordChangedAt;
    private int failedAttemptCount;
    private Instant lockedUntil;

    private Credential(Long userId, String passwordHash, int failedAttemptCount,
                       Instant lockedUntil, Instant passwordChangedAt) {

        this.userId = Objects.requireNonNull(userId, "userId must not be null");
        this.passwordHash = validatePasswordHash(passwordHash);
        this.failedAttemptCount = validateFailedAttemptCount(failedAttemptCount);
        this.lockedUntil = lockedUntil;
        this.passwordChangedAt = Objects.requireNonNull(passwordChangedAt, "passwordChangedAt must not be null");
    }

    public static Credential create(Long userId, String passwordHash, Instant passwordChangedAt) {
        return new Credential(userId, passwordHash, 0, null, passwordChangedAt);
    }

    public static Credential restore(Long userId, String passwordHash, int failedAttemptCount,
                                     Instant lockedUntil, Instant passwordChangedAt) {

        return new Credential(userId, passwordHash, failedAttemptCount, lockedUntil, passwordChangedAt);
    }

    public boolean isLockedAt(Instant instant) {
        Objects.requireNonNull(instant, "instant must not be null");
        return lockedUntil != null && lockedUntil.isAfter(instant);
    }

    public void registerFailedAttempt(Instant occurredAt, int maxFailures, Duration lockDuration) {
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        validateLockPolicy(maxFailures, lockDuration);

        if (isLockedAt(occurredAt)) {
            return;
        }

        resetExpiredLock(occurredAt);

        failedAttemptCount++;

        if (failedAttemptCount >= maxFailures) {
            failedAttemptCount = maxFailures;
            lockedUntil = occurredAt.plus(lockDuration);
        }
    }

    public void resetFailedAttempts() {
        failedAttemptCount = 0;
        lockedUntil = null;
    }

    private void resetExpiredLock(Instant instant) {
        if (lockedUntil != null && !lockedUntil.isAfter(instant)) {
            failedAttemptCount = 0;
            lockedUntil = null;
        }
    }

    private static void validateLockPolicy(int maxFailures, Duration lockDuration) {
        Objects.requireNonNull(lockDuration, "lockDuration must not be null");

        if (maxFailures < 1) {
            throw new IllegalArgumentException("maxFailures must be greater than zero");
        }

        if (lockDuration.isZero() || lockDuration.isNegative()) {
            throw new IllegalArgumentException("lockDuration must be positive");
        }
    }

    private static int validateFailedAttemptCount(int failedAttemptCount) {
        if (failedAttemptCount < 0) {
            throw new IllegalArgumentException("failedAttemptCount must not be negative");
        }

        return failedAttemptCount;
    }

    private static String validatePasswordHash(String passwordHash) {
        if (passwordHash == null || passwordHash.isBlank()) {
            throw new IllegalArgumentException("passwordHash must not be blank");
        }

        return passwordHash;
    }
}
