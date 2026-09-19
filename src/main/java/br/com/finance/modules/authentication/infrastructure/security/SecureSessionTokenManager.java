package br.com.finance.modules.authentication.infrastructure.security;

import br.com.finance.modules.authentication.application.port.out.SessionTokenManager;
import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Objects;
import java.util.regex.Pattern;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.regex.Pattern.*;

@Component
class SecureSessionTokenManager implements SessionTokenManager {

    private static final int TOKEN_BYTES = 32;
    private static final int TOKEN_LENGTH = (TOKEN_BYTES * 8 + 5) / 6;
    private static final String HASH_ALGORITHM = "SHA-256";
    private static final Pattern TOKEN_PATTERN = compile("[A-Za-z0-9_-]{" + TOKEN_LENGTH + "}");

    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    public String generate() {
        var token = new byte[TOKEN_BYTES];

        secureRandom.nextBytes(token);

        return Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(token);
    }

    @Override
    public String hash(String rawToken) {
        Objects.requireNonNull(rawToken, "rawToken must not be null");

        try {
            var digest = MessageDigest.getInstance(HASH_ALGORITHM);

            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("Required token hashing algorithm is unavailable", ex);
        }
    }

    @Override
    public boolean isInvalidFormat(String rawToken) {
        return rawToken == null || !TOKEN_PATTERN.matcher(rawToken).matches();
    }
}
