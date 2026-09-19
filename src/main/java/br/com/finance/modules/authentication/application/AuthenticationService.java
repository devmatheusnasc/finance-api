package br.com.finance.modules.authentication.application;

import br.com.finance.modules.authentication.application.command.LoginCommand;
import br.com.finance.modules.authentication.application.command.RegisterCommand;
import br.com.finance.modules.comum.exception.InvalidCredentialsException;
import br.com.finance.modules.comum.exception.LoginRateLimitExceededException;
import br.com.finance.modules.comum.exception.RegistrationRateLimitExceededException;
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
import br.com.finance.modules.authentication.domain.model.Credential;
import br.com.finance.modules.authentication.domain.model.Session;
import br.com.finance.modules.authentication.domain.model.User;

import java.text.Normalizer;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;

public class AuthenticationService implements AuthenticationUseCase {

    private final Clock clock;
    private final AuthenticationPolicy policy;
    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final SessionRepository sessionRepository;
    private final SessionTokenManager sessionTokenManager;
    private final LoginAttemptLimiter loginAttemptLimiter;
    private final RegistrationAttemptLimiter registrationAttemptLimiter;
    private final CredentialRepository credentialRepository;

    public AuthenticationService(UserRepository userRepository,
                                 CredentialRepository credentialRepository,
                                 SessionRepository sessionRepository,
                                 PasswordHasher passwordHasher,
                                 SessionTokenManager sessionTokenManager,
                                 LoginAttemptLimiter loginAttemptLimiter,
                                 RegistrationAttemptLimiter registrationAttemptLimiter,
                                 AuthenticationPolicy policy,
                                 Clock clock) {

        this.userRepository = userRepository;
        this.credentialRepository = credentialRepository;
        this.sessionRepository = sessionRepository;
        this.passwordHasher = passwordHasher;
        this.sessionTokenManager = sessionTokenManager;
        this.loginAttemptLimiter = loginAttemptLimiter;
        this.registrationAttemptLimiter = registrationAttemptLimiter;
        this.policy = policy;
        this.clock = clock;
    }

    @Override
    public void register(RegisterCommand command) {
        var now = clock.instant();

        validateRegistrationRateLimit(command.sourceAddress(), now);

        var passwordHash = passwordHasher.hash(command.password());
        var user = User.create(command.name().strip(), command.email().strip(), normalizeEmail(command.email()), now);

        userRepository.saveIfEmailAvailable(user)
            .ifPresent(savedUser ->
                credentialRepository.save(Credential.create(savedUser.getId(), passwordHash, now))
            );
    }

    @Override
    public IssuedSession login(LoginCommand command) {
        var now = clock.instant();
        var normalizedEmail = normalizeEmail(command.email());

        validateRateLimit(normalizedEmail, command.sourceAddress(), now);

        var user = userRepository.findByNormalizedEmail(normalizedEmail);

        if (user.isEmpty()) {
            passwordHasher.performDummyVerification(command.password());

            throw invalidCredentials(normalizedEmail, command.sourceAddress(), now);
        }

        var resolvedUser = user.get();
        var credential = credentialRepository.findByUserIdForUpdate(resolvedUser.getId());

        if (credential.isEmpty()) {
            passwordHasher.performDummyVerification(command.password());

            throw invalidCredentials(normalizedEmail, command.sourceAddress(), now);
        }

        var resolvedCredential = credential.get();

        validateCredentials(command, normalizedEmail, resolvedUser, resolvedCredential, now);

        return createSession(resolvedUser, resolvedCredential, normalizedEmail, now);
    }

    @Override
    public void logout(String rawSessionToken) {
        if (sessionTokenManager.isInvalidFormat(rawSessionToken)) {
            return;
        }

        var tokenHash = sessionTokenManager.hash(rawSessionToken);
        sessionRepository.revokeByTokenHash(tokenHash, clock.instant());
    }

    @Override
    public Optional<AuthenticatedUser> authenticate(String rawSessionToken) {
        if (sessionTokenManager.isInvalidFormat(rawSessionToken)) {
            return Optional.empty();
        }

        var tokenHash = sessionTokenManager.hash(rawSessionToken);
        var now = clock.instant();

        return sessionRepository.findByTokenHashForAuthentication(tokenHash)
            .filter(session -> session.isValidAt(now, policy.sessionIdleTimeout()))
            .flatMap(session -> authenticatedUser(session, now));
    }

    private Optional<AuthenticatedUser> authenticatedUser(Session session, Instant now) {
        return userRepository.findById(session.getUserId())
            .filter(User::isActive)
            .map(user -> {
                recordActivityIfNecessary(session, now);

                return AuthenticatedUser.of(user);
            });
    }

    private void recordActivityIfNecessary(Session session, Instant now) {
        if (!session.isActivityUpdateDueAt(now, policy.sessionActivityUpdateInterval())) {
            return;
        }

        sessionRepository.recordActivity(session, now);
    }

    private void validateRegistrationRateLimit(String sourceAddress, Instant now) {
        if (!registrationAttemptLimiter.tryAcquire(sourceAddress, now)) {
            throw new RegistrationRateLimitExceededException();
        }
    }

    private void validateRateLimit(String normalizedEmail, String sourceAddress, Instant now) {
        if (loginAttemptLimiter.isBlocked(normalizedEmail, sourceAddress, now)) {
            throw new LoginRateLimitExceededException();
        }
    }

    private void validateCredentials(LoginCommand command, String normalizedEmail,
                                     User user, Credential credential, Instant now) {

        if (!user.isActive() || credential.isLockedAt(now)) {
            passwordHasher.performDummyVerification(command.password());
            throw invalidCredentials(normalizedEmail, command.sourceAddress(), now);
        }

        if (passwordHasher.matches(command.password(), credential.getPasswordHash())) {
            return;
        }

        credential.registerFailedAttempt(now, policy.credentialMaxFailures(), policy.credentialLockDuration());
        credentialRepository.save(credential);

        throw invalidCredentials(normalizedEmail, command.sourceAddress(), now);
    }

    private IssuedSession createSession(User user, Credential credential, String normalizedEmail, Instant now) {
        credential.resetFailedAttempts();
        credentialRepository.save(credential);

        loginAttemptLimiter.registerSuccess(normalizedEmail);

        var rawToken = sessionTokenManager.generate();
        var expiresAt = now.plus(policy.sessionDuration());

        var session = Session.create(user.getId(), sessionTokenManager.hash(rawToken), now, expiresAt);

        sessionRepository.save(session);
        return new IssuedSession(rawToken, expiresAt);
    }

    private InvalidCredentialsException invalidCredentials(String normalizedEmail, String sourceAddress,
                                                           Instant occurredAt) {

        loginAttemptLimiter.registerFailure(normalizedEmail, sourceAddress, occurredAt);

        return new InvalidCredentialsException();
    }

    private String normalizeEmail(String email) {
        return Normalizer.normalize(email.strip(), Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
    }
}
