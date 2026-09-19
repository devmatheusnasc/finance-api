package br.com.finance.modules.authentication.application.command;

import java.util.Objects;

public record RegisterCommand(
        String name,
        String email,
        String password,
        String sourceAddress
) {

    public RegisterCommand {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(email, "email must not be null");
        Objects.requireNonNull(password, "password must not be null");
        Objects.requireNonNull(sourceAddress, "sourceAddress must not be null");
    }

    @Override
    @SuppressWarnings("NullableProblems")
    public String toString() {
        return "RegisterCommand[redacted]";
    }
}
