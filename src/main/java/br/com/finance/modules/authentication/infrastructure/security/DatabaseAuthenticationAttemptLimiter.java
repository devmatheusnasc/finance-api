package br.com.finance.modules.authentication.infrastructure.security;

import br.com.finance.modules.authentication.application.port.out.LoginAttemptLimiter;
import br.com.finance.modules.authentication.application.port.out.RegistrationAttemptLimiter;
import br.com.finance.modules.authentication.infrastructure.config.AuthenticationProperties;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;

import static org.springframework.transaction.annotation.Propagation.MANDATORY;

@Component
@Transactional(propagation = MANDATORY)
class DatabaseAuthenticationAttemptLimiter implements LoginAttemptLimiter, RegistrationAttemptLimiter {

    private static final String ACCOUNT_SCOPE = "ACCOUNT";
    private static final String SOURCE_SCOPE = "SOURCE";
    private static final String REGISTRATION_SOURCE_SCOPE = "REGISTRATION_SOURCE";
    private static final String UNKNOWN_SOURCE = "unknown";

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final AuthenticationAttemptKeyHasher keyHasher;
    private final AuthenticationProperties.RateLimit accountPolicy;
    private final AuthenticationProperties.RateLimit sourcePolicy;
    private final AuthenticationProperties.AttemptRateLimit registrationPolicy;

    DatabaseAuthenticationAttemptLimiter(NamedParameterJdbcTemplate jdbcTemplate, AuthenticationAttemptKeyHasher keyHasher,
                                         AuthenticationProperties properties) {

        this.jdbcTemplate = jdbcTemplate;
        this.keyHasher = keyHasher;
        this.accountPolicy = properties.accountRateLimit();
        this.sourcePolicy = properties.sourceRateLimit();
        this.registrationPolicy = properties.registrationRateLimit();
    }

    @Override
    public boolean isBlocked(String normalizedEmail, String sourceAddress, Instant checkedAt) {
        var accountKey = accountKey(normalizedEmail);
        var sourceKey = sourceKey(SOURCE_SCOPE, sourceAddress);

        acquireTransactionLocks(sourceKey, accountKey);

        var parameters = new MapSqlParameterSource()
            .addValue("accountScope", ACCOUNT_SCOPE)
            .addValue("accountKey", accountKey)
            .addValue("sourceScope", SOURCE_SCOPE)
            .addValue("sourceKey", sourceKey)
            .addValue("checkedAt", toUtc(checkedAt));

        return Boolean.TRUE.equals(
            jdbcTemplate.queryForObject(
                """
                SELECT EXISTS (
                    SELECT 1
                      FROM authentication_login_throttle
                     WHERE blocked_until > :checkedAt
                       AND (
                            (scope = :accountScope AND key_hash = :accountKey)
                         OR (scope = :sourceScope AND key_hash = :sourceKey)
                       )
                )
                """,
                parameters,
                Boolean.class
            )
        );
    }

    @Override
    public void registerFailure(String normalizedEmail, String sourceAddress, Instant occurredAt) {
        registerAttempt(
            ACCOUNT_SCOPE,
            accountKey(normalizedEmail),
            occurredAt,
            accountPolicy.maxFailures(),
            accountPolicy.window(),
            accountPolicy.blockDuration()
        );
        registerAttempt(
            SOURCE_SCOPE,
            sourceKey(SOURCE_SCOPE, sourceAddress),
            occurredAt,
            sourcePolicy.maxFailures(),
            sourcePolicy.window(),
            sourcePolicy.blockDuration()
        );
    }

    @Override
    public void registerSuccess(String normalizedEmail) {
        var parameters = new MapSqlParameterSource()
            .addValue("scope", ACCOUNT_SCOPE)
            .addValue("keyHash", accountKey(normalizedEmail));

        jdbcTemplate.update(
            """
            DELETE FROM authentication_login_throttle
             WHERE scope = :scope
               AND key_hash = :keyHash
            """,
            parameters
        );
    }

    @Override
    public boolean tryAcquire(String sourceAddress, Instant attemptedAt) {
        var keyHash = sourceKey(REGISTRATION_SOURCE_SCOPE, sourceAddress);

        acquireTransactionLocks(keyHash);

        if (isScopeBlocked(REGISTRATION_SOURCE_SCOPE, keyHash, attemptedAt)) {
            return false;
        }

        registerAttempt(
            REGISTRATION_SOURCE_SCOPE,
            keyHash,
            attemptedAt,
            registrationPolicy.maxAttempts(),
            registrationPolicy.window(),
            registrationPolicy.blockDuration()
        );
        return true;
    }

    private boolean isScopeBlocked(String scope, String keyHash, Instant checkedAt) {
        var parameters = new MapSqlParameterSource()
            .addValue("scope", scope)
            .addValue("keyHash", keyHash)
            .addValue("checkedAt", toUtc(checkedAt));

        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
            """
            SELECT EXISTS (
                SELECT 1
                  FROM authentication_login_throttle
                 WHERE scope = :scope
                   AND key_hash = :keyHash
                   AND blocked_until > :checkedAt
            )
            """,
            parameters,
            Boolean.class
        ));
    }

    private void registerAttempt(String scope, String keyHash, Instant occurredAt, int maxAttempts,
                                 Duration window, Duration blockDuration) {

        var parameters = attemptParameters(
            scope,
            keyHash,
            occurredAt,
            maxAttempts,
            window,
            blockDuration
        );

        createThrottleIfNecessary(parameters);
        incrementAttempt(parameters);
    }

    private void createThrottleIfNecessary(MapSqlParameterSource parameters) {
        jdbcTemplate.update(
            """
            INSERT INTO authentication_login_throttle (
                scope,
                key_hash,
                window_started_at,
                failure_count,
                blocked_until,
                updated_at
            )
            VALUES (
                :scope,
                :keyHash,
                :occurredAt,
                0,
                NULL,
                :occurredAt
            )
            ON CONFLICT (scope, key_hash) DO NOTHING
            """,
            parameters
        );
    }

    private void incrementAttempt(MapSqlParameterSource parameters) {
        jdbcTemplate.update(
            """
            UPDATE authentication_login_throttle
               SET failure_count =
                       CASE
                           WHEN window_started_at <= :windowThreshold
                             OR (blocked_until IS NOT NULL
                                 AND blocked_until <= :occurredAt)
                               THEN 1
                           ELSE failure_count + 1
                       END,

                   window_started_at =
                       CASE
                           WHEN window_started_at <= :windowThreshold
                             OR (blocked_until IS NOT NULL
                                 AND blocked_until <= :occurredAt)
                               THEN :occurredAt
                           ELSE window_started_at
                       END,

                   blocked_until =
                       CASE
                           WHEN blocked_until > :occurredAt
                               THEN blocked_until

                           WHEN (
                               CASE
                                   WHEN window_started_at <= :windowThreshold
                                     OR (blocked_until IS NOT NULL
                                         AND blocked_until <= :occurredAt)
                                       THEN 1
                                   ELSE failure_count + 1
                               END
                           ) >= :maxAttempts
                               THEN :blockedUntil

                           ELSE NULL
                       END,

                   updated_at = :occurredAt

             WHERE scope = :scope
               AND key_hash = :keyHash
            """,
            parameters
        );
    }

    private MapSqlParameterSource attemptParameters(String scope, String keyHash, Instant occurredAt,
                                                    int maxAttempts, Duration window, Duration blockDuration) {

        return new MapSqlParameterSource()
            .addValue("scope", scope)
            .addValue("keyHash", keyHash)
            .addValue("occurredAt", toUtc(occurredAt))
            .addValue("windowThreshold", toUtc(occurredAt.minus(window)))
            .addValue("blockedUntil", toUtc(occurredAt.plus(blockDuration)))
            .addValue("maxAttempts", maxAttempts);
    }

    private void acquireTransactionLocks(String... keyHashes) {
        Arrays.stream(keyHashes)
            .mapToLong(this::toAdvisoryLockKey)
            .distinct()
            .sorted()
            .forEach(this::acquireTransactionLock);
    }

    private long toAdvisoryLockKey(String keyHash) {
        return Long.parseUnsignedLong(keyHash, 0, Long.BYTES * 2, 16);
    }

    private void acquireTransactionLock(long lockKey) {
        var parameters = new MapSqlParameterSource()
            .addValue("lockKey", lockKey);

        jdbcTemplate.query(
            "SELECT pg_advisory_xact_lock(:lockKey)",
            parameters,
            resultSet -> null
        );
    }

    private String accountKey(String normalizedEmail) {
        return keyHasher.hash(ACCOUNT_SCOPE, normalizedEmail);
    }

    private String sourceKey(String scope, String sourceAddress) {
        return keyHasher.hash(scope, normalizeSource(sourceAddress));
    }

    private String normalizeSource(String sourceAddress) {
        return sourceAddress == null || sourceAddress.isBlank()
            ? UNKNOWN_SOURCE
            : sourceAddress.strip();
    }

    private OffsetDateTime toUtc(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
