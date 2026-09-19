package br.com.finance.modules.authentication.application.port.in;

import br.com.finance.modules.authentication.application.command.LoginCommand;
import br.com.finance.modules.authentication.application.command.RegisterCommand;
import br.com.finance.modules.authentication.application.result.AuthenticatedUser;
import br.com.finance.modules.authentication.application.result.IssuedSession;

import java.util.Optional;

public interface AuthenticationUseCase {

    void register(RegisterCommand command);

    IssuedSession login(LoginCommand command);

    void logout(String rawSessionToken);

    Optional<AuthenticatedUser> authenticate(String rawSessionToken);
}
