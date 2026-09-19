package br.com.finance.modules.authentication.infrastructure.security;

import br.com.finance.modules.authentication.infrastructure.config.AuthenticationProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import java.util.Objects;

import static java.nio.charset.StandardCharsets.UTF_8;

@Component
class AuthenticationAttemptKeyHasher {

    private static final String ALGORITHM = "HmacSHA256";
    private static final byte SEPARATOR = 0;

    private final SecretKeySpec secretKey;

    AuthenticationAttemptKeyHasher(AuthenticationProperties properties) {
        var secret = Objects.requireNonNull(properties.rateLimitKeySecret(), "rateLimitKeySecret must not be null");

        this.secretKey = new SecretKeySpec(secret.getBytes(UTF_8), ALGORITHM);
    }

    String hash(String scope, String value) {
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(value, "value must not be null");

        try {
            var mac = Mac.getInstance(ALGORITHM);
            mac.init(secretKey);

            mac.update(scope.getBytes(UTF_8));
            mac.update(SEPARATOR);

            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(UTF_8)));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Unable to calculate login attempt key hash", ex);
        }
    }
}
