package br.com.finance.modules.authentication.application.command;

public record LoginCommand(
        String email,
        String password,
        String sourceAddress
) {
}