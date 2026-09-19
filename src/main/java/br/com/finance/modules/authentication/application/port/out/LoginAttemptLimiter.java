package br.com.finance.modules.authentication.application.port.out;

import java.time.Instant;

public interface LoginAttemptLimiter {

    boolean isBlocked(String normalizedEmail, String sourceAddress, Instant checkedAt);

    void registerFailure(String normalizedEmail, String sourceAddress, Instant occurredAt);

    void registerSuccess(String normalizedEmail);
}
