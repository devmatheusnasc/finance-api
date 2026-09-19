package br.com.finance.modules.authentication.infrastructure;

import br.com.finance.modules.authentication.application.AuthenticationService;
import br.com.finance.modules.authentication.application.command.LoginCommand;
import br.com.finance.modules.authentication.application.command.RegisterCommand;
import br.com.finance.modules.comum.exception.InvalidCredentialsException;
import br.com.finance.modules.authentication.application.result.AuthenticatedUser;
import br.com.finance.modules.authentication.application.policy.AuthenticationPolicy;
import br.com.finance.modules.authentication.application.result.IssuedSession;
import br.com.finance.modules.authentication.application.port.in.AuthenticationUseCase;
import br.com.finance.modules.authentication.application.port.out.CredentialRepository;
import br.com.finance.modules.authentication.application.port.out.LoginAttemptLimiter;
import br.com.finance.modules.authentication.application.port.out.PasswordHasher;
import br.com.finance.modules.authentication.application.port.out.RegistrationAttemptLimiter;
import br.com.finance.modules.authentication.application.port.out.SessionRepository;
import br.com.finance.modules.authentication.application.port.out.SessionTokenManager;
import br.com.finance.modules.authentication.application.port.out.UserRepository;
import br.com.finance.modules.comum.exception.RegistrationRateLimitExceededException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Optional;

@Component
public class AuthenticationApplicationAdapter implements AuthenticationUseCase {

    private final AuthenticationService service;

    public AuthenticationApplicationAdapter(UserRepository userRepository,
                                            CredentialRepository credentialRepository,
                                            SessionRepository sessionRepository,
                                            PasswordHasher passwordHasher,
                                            SessionTokenManager sessionTokenManager,
                                            LoginAttemptLimiter loginAttemptLimiter,
                                            RegistrationAttemptLimiter registrationAttemptLimiter,
                                            AuthenticationPolicy policy,
                                            Clock clock) {

        this.service = new AuthenticationService(
            userRepository,
            credentialRepository,
            sessionRepository,
            passwordHasher,
            sessionTokenManager,
            loginAttemptLimiter,
            registrationAttemptLimiter,
            policy,
            clock
        );
    }

    @Override
    @Transactional
    public void register(RegisterCommand command) {
        service.register(command);
    }

    @Override
    @Transactional(noRollbackFor = InvalidCredentialsException.class)
    public IssuedSession login(LoginCommand command) {
        return service.login(command);
    }

    @Override
    @Transactional
    public void logout(String rawSessionToken) {
        service.logout(rawSessionToken);
    }

    @Override
    @Transactional
    public Optional<AuthenticatedUser> authenticate(String rawSessionToken) {
        return service.authenticate(rawSessionToken);
    }
}
