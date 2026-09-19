package br.com.finance.modules.authentication.domain.model;

import lombok.Getter;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

@Getter
public final class Session {

    private final Long id;
    private final Long userId;
    private final String tokenHash;
    private final Instant createdAt;
    private final Instant lastActivityAt;
    private final Instant expiresAt;
    private final Instant revokedAt;

    private Session(Long id, Long userId, String tokenHash, Instant createdAt, Instant lastActivityAt,
                    Instant expiresAt, Instant revokedAt) {

        this.id = id;
        this.userId = Objects.requireNonNull(userId, "userId must not be null");
        this.tokenHash = validateTokenHash(tokenHash);
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.lastActivityAt = Objects.requireNonNull(lastActivityAt, "lastActivityAt must not be null");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        this.revokedAt = revokedAt;

        validateExpiration();
        validateLastActivity();
        validateRevocation();
    }

    public static Session create(Long userId, String tokenHash, Instant createdAt, Instant expiresAt) {
        return new Session(null, userId, tokenHash, createdAt, createdAt, expiresAt, null);
    }

    public static Session restore(Long id, Long userId, String tokenHash, Instant createdAt, Instant lastActivityAt,
                                  Instant expiresAt, Instant revokedAt) {

        return new Session(Objects.requireNonNull(id, "id must not be null"),
            userId, tokenHash, createdAt, lastActivityAt, expiresAt, revokedAt);
    }

    public Session revoke(Instant revokedAt) {
        Objects.requireNonNull(revokedAt, "revokedAt must not be null");

        if (isRevoked()) {
            return this;
        }

        return new Session(id, userId, tokenHash, createdAt, lastActivityAt, expiresAt, revokedAt);
    }

    public boolean isValidAt(Instant instant, Duration idleTimeout) {
        Objects.requireNonNull(instant, "instant must not be null");
        Objects.requireNonNull(idleTimeout, "idleTimeout must not be null");

        if (idleTimeout.isZero() || idleTimeout.isNegative()) {
            throw new IllegalArgumentException("idleTimeout must be positive");
        }

        return !instant.isBefore(createdAt)
            && !isExpiredAt(instant)
            && !isIdleAt(instant, idleTimeout)
            && !isRevoked();
    }

    public boolean isActivityUpdateDueAt(Instant instant, Duration updateInterval) {
        Objects.requireNonNull(instant, "instant must not be null");
        Objects.requireNonNull(updateInterval, "updateInterval must not be null");
        requirePositive(updateInterval, "updateInterval");

        return !instant.isBefore(lastActivityAt.plus(updateInterval));
    }

    public boolean isExpiredAt(Instant instant) {
        Objects.requireNonNull(instant, "instant must not be null");

        return !instant.isBefore(expiresAt);
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    private boolean isIdleAt(Instant instant, Duration idleTimeout) {
        return !instant.isBefore(lastActivityAt.plus(idleTimeout));
    }

    private void validateExpiration() {
        if (!expiresAt.isAfter(createdAt)) {
            throw new IllegalArgumentException("expiresAt must be after createdAt");
        }
    }

    private void validateLastActivity() {
        if (lastActivityAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("lastActivityAt must not be before createdAt");
        }
    }

    private void validateRevocation() {
        if (revokedAt != null && revokedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("revokedAt must not be before createdAt");
        }
    }

    private static String validateTokenHash(String tokenHash) {
        if (tokenHash == null || tokenHash.isBlank()) {
            throw new IllegalArgumentException("tokenHash must not be blank");
        }

        return tokenHash;
    }

    private static void requirePositive(Duration duration, String fieldName) {
        Objects.requireNonNull(duration, fieldName + " must not be null");

        if (duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException(fieldName + " must be positive");
        }
    }
}
