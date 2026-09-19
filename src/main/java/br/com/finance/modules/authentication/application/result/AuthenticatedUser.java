package br.com.finance.modules.authentication.application.result;

import br.com.finance.modules.authentication.domain.model.User;

import java.util.Objects;

public record AuthenticatedUser(
        Long id,
        String name,
        String email
) {

    public static AuthenticatedUser of(User user) {
        Objects.requireNonNull(user, "user must not be null");

        return new AuthenticatedUser(
            user.getId(),
            user.getName(),
            user.getEmail()
        );
    }
}