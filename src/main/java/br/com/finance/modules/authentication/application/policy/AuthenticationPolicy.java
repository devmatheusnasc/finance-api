package br.com.finance.modules.authentication.application.policy;

import java.time.Duration;
import java.util.Objects;

public record AuthenticationPolicy(

        Duration sessionDuration,
        Duration sessionIdleTimeout,
        Duration sessionActivityUpdateInterval,
        int credentialMaxFailures,
        Duration credentialLockDuration

) {

    public AuthenticationPolicy {
        requirePositive(sessionDuration, "sessionDuration");
        requirePositive(sessionIdleTimeout, "sessionIdleTimeout");
        requirePositive(sessionActivityUpdateInterval, "sessionActivityUpdateInterval");
        requirePositive(credentialLockDuration, "credentialLockDuration");

        if (sessionIdleTimeout.compareTo(sessionDuration) > 0) {
            throw new IllegalArgumentException("sessionIdleTimeout must not exceed sessionDuration");
        }

        if (sessionActivityUpdateInterval.compareTo(sessionIdleTimeout) >= 0) {
            throw new IllegalArgumentException("sessionActivityUpdateInterval must be shorter than sessionIdleTimeout");
        }

        if (credentialMaxFailures < 1) {
            throw new IllegalArgumentException("credentialMaxFailures must be greater than zero");
        }
    }

    private static void requirePositive(Duration duration, String fieldName) {
        Objects.requireNonNull(duration, fieldName + " must not be null");

        if (duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException(fieldName + " must be positive");
        }
    }
}
