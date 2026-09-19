package br.com.finance.modules.authentication.infrastructure.security;

import br.com.finance.modules.authentication.application.port.out.PasswordHasher;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;

@Component
class Argon2PasswordHasher implements PasswordHasher {

    private static final int SALT_LENGTH = 16;
    private static final int HASH_LENGTH = 32;
    private static final int PARALLELISM = 1;
    private static final int MEMORY_IN_KIB = 19_456;
    private static final int ITERATIONS = 2;
    private static final int DUMMY_PASSWORD_BYTES = 32;

    private final Argon2PasswordEncoder passwordEncoder;
    private final String dummyPasswordHash;

    Argon2PasswordHasher() {
        this.passwordEncoder = new Argon2PasswordEncoder(SALT_LENGTH, HASH_LENGTH,
            PARALLELISM, MEMORY_IN_KIB, ITERATIONS);
        this.dummyPasswordHash = createDummyPasswordHash();
    }

    @Override
    public String hash(String rawPassword) {
        Objects.requireNonNull(rawPassword, "rawPassword must not be null");

        return passwordEncoder.encode(rawPassword);
    }

    @Override
    public boolean matches(String rawPassword, String passwordHash) {
        Objects.requireNonNull(rawPassword, "rawPassword must not be null");
        Objects.requireNonNull(passwordHash, "passwordHash must not be null");

        return passwordEncoder.matches(rawPassword, passwordHash);
    }

    @Override
    public void performDummyVerification(String rawPassword) {
        Objects.requireNonNull(rawPassword, "rawPassword must not be null");

        passwordEncoder.matches(rawPassword, dummyPasswordHash);
    }

    private String createDummyPasswordHash() {
        var randomBytes = new byte[DUMMY_PASSWORD_BYTES];
        SecureRandomHolder.INSTANCE.nextBytes(randomBytes);

        return passwordEncoder.encode(Base64.getEncoder().encodeToString(randomBytes));
    }

    private static final class SecureRandomHolder {
        private static final SecureRandom INSTANCE = new SecureRandom();

        private SecureRandomHolder() {
        }
    }
}