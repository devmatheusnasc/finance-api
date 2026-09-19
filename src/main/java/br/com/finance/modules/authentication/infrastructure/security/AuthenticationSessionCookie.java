package br.com.finance.modules.authentication.infrastructure.security;

import br.com.finance.modules.authentication.application.port.out.SessionTokenManager;
import br.com.finance.modules.authentication.application.result.IssuedSession;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseCookie.ResponseCookieBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.util.WebUtils;

import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

import static java.time.Duration.ZERO;

@Component
public class AuthenticationSessionCookie {

    public static final String NAME = "__Host-FINANCE_SESSION";
    private static final String SAME_SITE = "Strict";
    private static final String PATH = "/";

    private final Clock clock;
    private final SessionTokenManager sessionTokenManager;

    AuthenticationSessionCookie(Clock clock, SessionTokenManager sessionTokenManager) {
        this.clock = clock;
        this.sessionTokenManager = sessionTokenManager;
    }

    public Optional<String> read(HttpServletRequest request) {
        Objects.requireNonNull(request, "request must not be null");

        var cookie = WebUtils.getCookie(request, NAME);

        if (cookie == null || sessionTokenManager.isInvalidFormat(cookie.getValue())) {

            return Optional.empty();
        }

        return Optional.of(cookie.getValue());
    }

    public String create(IssuedSession session) {
        Objects.requireNonNull(session, "session must not be null");

        if (sessionTokenManager.isInvalidFormat(session.rawToken())) {
            throw new IllegalArgumentException("Session token has an invalid format");
        }

        return baseCookie(session.rawToken())
            .maxAge(remainingDuration(session))
            .build()
            .toString();
    }


    public String clear() {
        return baseCookie("")
            .maxAge(ZERO)
            .build()
            .toString();
    }

    private Duration remainingDuration(IssuedSession session) {
        var duration = Duration.between(clock.instant(), session.expiresAt());

        return duration.isNegative()
            ? Duration.ZERO
            : duration;
    }

    private ResponseCookieBuilder baseCookie(String value) {
        return ResponseCookie.from(NAME, value)
            .httpOnly(true)
            .secure(true)
            .sameSite(SAME_SITE)
            .path(PATH);
    }
}
