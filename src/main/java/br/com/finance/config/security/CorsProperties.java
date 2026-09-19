package br.com.finance.config.security;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.net.URI;
import java.util.List;
import java.util.Set;

@Validated
@ConfigurationProperties(prefix = "finance.security.cors")
public record CorsProperties(@NotEmpty List<String> allowedOrigins) {

    private static final Set<String> LOCAL_HOSTS = Set.of("localhost", "127.0.0.1");

    public CorsProperties {
        allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
    }

    @AssertTrue(message = "CORS origins must be explicit HTTP or HTTPS origins without wildcards")
    public boolean hasValidOrigins() {
        return allowedOrigins.stream().allMatch(CorsProperties::isValidOrigin);
    }

    private static boolean isValidOrigin(String origin) {
        if (origin.contains("*")) {
            return false;
        }

        try {
            var uri = URI.create(origin);

            return hasValidScheme(uri)
                && uri.getHost() != null
                && hasNoPath(uri)
                && uri.getUserInfo() == null
                && uri.getQuery() == null
                && uri.getFragment() == null;

        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private static boolean hasValidScheme(URI uri) {
        if ("https".equalsIgnoreCase(uri.getScheme())) {
            return true;
        }

        return "http".equalsIgnoreCase(uri.getScheme()) && LOCAL_HOSTS.contains(uri.getHost());
    }

    private static boolean hasNoPath(URI uri) {
        return uri.getPath() == null || uri.getPath().isEmpty();
    }
}
