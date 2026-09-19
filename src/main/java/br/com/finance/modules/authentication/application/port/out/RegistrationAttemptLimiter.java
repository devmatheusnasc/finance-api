package br.com.finance.modules.authentication.application.port.out;

import java.time.Instant;

public interface RegistrationAttemptLimiter {

    boolean tryAcquire(String sourceAddress, Instant attemptedAt);
}
