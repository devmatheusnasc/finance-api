package br.com.finance.modules.authentication.infrastructure.presentation.web.response;

import br.com.finance.modules.authentication.application.result.AuthenticatedUser;

public record AuthenticatedUserResponse(

        Long id,
        String name,
        String email
) {

    public static AuthenticatedUserResponse of(AuthenticatedUser user) {
        return new AuthenticatedUserResponse(
            user.id(),
            user.name(),
            user.email()
        );
    }
}