package br.com.finance.modules.authentication.infrastructure.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "finance.security.authentication")
public record AuthenticationProperties(

    @NotBlank
    @Size(min = 32, max = 256)
    String rateLimitKeySecret,

    @NotNull
    Duration sessionDuration,

    @NotNull
    Duration sessionIdleTimeout,

    @NotNull
    Duration sessionActivityUpdateInterval,

    @Min(2)
    @Max(20)
    int credentialMaxFailures,

    @NotNull
    Duration credentialLockDuration,

    @Valid
    @NotNull
    RateLimit accountRateLimit,

    @Valid
    @NotNull
    RateLimit sourceRateLimit,

    @Valid
    @NotNull
    AttemptRateLimit registrationRateLimit

) {

    private static final Duration MIN_DURATION = Duration.ofMinutes(1);
    private static final Duration MAX_DURATION = Duration.ofHours(24);

    public AuthenticationProperties {
        validateDuration(sessionDuration, "sessionDuration");
        validateDuration(sessionIdleTimeout, "sessionIdleTimeout");
        validateDuration(sessionActivityUpdateInterval, "sessionActivityUpdateInterval");
        validateDuration(credentialLockDuration, "credentialLockDuration");

        validateSessionPolicy(sessionDuration, sessionIdleTimeout, sessionActivityUpdateInterval);
    }

    public record RateLimit(

            @Min(2)
            @Max(1000)
            int maxFailures,

            @NotNull
            Duration window,

            @NotNull
            Duration blockDuration
    ) {

        public RateLimit {
            validateDuration(window,  "rateLimit.window");
            validateDuration(blockDuration, "rateLimit.blockDuration");
        }
    }

    public record AttemptRateLimit(

            @Min(1)
            @Max(1000)
            int maxAttempts,

            @NotNull
            Duration window,

            @NotNull
            Duration blockDuration
    ) {

        public AttemptRateLimit {
            validateDuration(window, "registrationRateLimit.window");
            validateDuration(blockDuration, "registrationRateLimit.blockDuration");
        }
    }

    private static void validateSessionPolicy(Duration sessionDuration, Duration sessionIdleTimeout,
                                              Duration sessionActivityUpdateInterval) {

        if (sessionDuration != null && sessionIdleTimeout != null
                && sessionIdleTimeout.compareTo(sessionDuration) > 0) {

            throw new IllegalArgumentException("sessionIdleTimeout must not exceed sessionDuration");
        }

        if (sessionIdleTimeout != null && sessionActivityUpdateInterval != null
                && sessionActivityUpdateInterval.compareTo(sessionIdleTimeout) >= 0) {

            throw new IllegalArgumentException("sessionActivityUpdateInterval must be shorter than sessionIdleTimeout");
        }
    }

    private static void validateDuration(Duration value, String field) {
        if (value == null) {
            return;
        }

        if (value.compareTo(MIN_DURATION) < 0 || value.compareTo(MAX_DURATION) > 0) {
            throw new IllegalArgumentException(field + " must be between 1 minute and 24 hours");
        }
    }
}
