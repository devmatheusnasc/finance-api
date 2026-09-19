package br.com.finance.modules.authentication.application.result;

import java.time.Instant;
import java.util.Objects;

public record IssuedSession(
        String rawToken,
        Instant expiresAt
) {

    public IssuedSession {
        if (rawToken == null || rawToken.isBlank()) {
            throw new IllegalArgumentException("rawToken must not be blank");
        }

        Objects.requireNonNull(expiresAt, "expiresAt must not be null");
    }

    @Override
    @SuppressWarnings("NullableProblems")
    public String toString() {
        return "IssuedSession[rawToken=***, expiresAt=" + expiresAt + "]";
    }
}