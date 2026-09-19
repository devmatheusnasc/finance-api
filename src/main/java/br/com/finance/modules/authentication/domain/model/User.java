package br.com.finance.modules.authentication.domain.model;

import lombok.Getter;

import java.time.Instant;
import java.util.Objects;

@Getter
public final class User {

    private final Long id;
    private final String name;
    private final String email;
    private final String normalizedEmail;
    private final boolean isActive;
    private final Instant createdAt;
    private final Instant updatedAt;

    private User(Long id, String name, String email, String normalizedEmail,
                 boolean isActive, Instant createdAt, Instant updatedAt) {

        this.id = id;
        this.name = validateNotBlank(name, "name");
        this.email = validateNotBlank(email, "email");
        this.normalizedEmail = validateNotBlank(normalizedEmail, "normalizedEmail");
        this.isActive = isActive;
        this.createdAt = Objects.requireNonNull(createdAt);
        this.updatedAt = Objects.requireNonNull(updatedAt);

        validateDates();
    }

    public static User create(String name, String email, String normalizedEmail, Instant createdAt) {
        return new User(null, name, email, normalizedEmail, true, createdAt, createdAt);
    }

    public static User restore(Long id, String name, String email, String normalizedEmail,
                               boolean isActive, Instant createdAt, Instant updatedAt) {

        return new User(Objects.requireNonNull(id), name, email, normalizedEmail, isActive, createdAt, updatedAt);
    }

    private void validateDates() {
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("updatedAt must not be before createdAt");
        }
    }

    private static String validateNotBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }

        return value;
    }
}
