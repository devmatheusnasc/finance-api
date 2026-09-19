package br.com.finance.modules.authentication.application;

import br.com.finance.modules.authentication.application.command.LoginCommand;
import br.com.finance.modules.authentication.application.command.RegisterCommand;
import br.com.finance.modules.comum.exception.InvalidCredentialsException;
import br.com.finance.modules.comum.exception.LoginRateLimitExceededException;
import br.com.finance.modules.comum.exception.RegistrationRateLimitExceededException;
import br.com.finance.modules.authentication.application.policy.AuthenticationPolicy;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static br.com.finance.modules.authentication.application.AuthenticationFixtures.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {

    private static final String EMAIL = "User@Finance.Test";
    private static final String NORMALIZED_EMAIL = "user@finance.test";
    private static final String PASSWORD = UUID.randomUUID() + "!Aa1";
    private static final String SOURCE_ADDRESS = "198.51.100." + 10;
    private static final String RAW_TOKEN = "raw-" + UUID.randomUUID();
    private static final String HASHED_TOKEN = "hashed-" + UUID.randomUUID();

    @Mock
    private UserRepository userRepository;
    @Mock
    private CredentialRepository credentialRepository;
    @Mock
    private SessionRepository sessionRepository;
    @Mock
    private PasswordHasher passwordHasher;
    @Mock
    private SessionTokenManager sessionTokenManager;
    @Mock
    private LoginAttemptLimiter loginAttemptLimiter;
    @Mock
    private RegistrationAttemptLimiter registrationAttemptLimiter;

    private AuthenticationService authenticationService;

    @BeforeEach
    void setUp() {
        authenticationService = new AuthenticationService(
            userRepository,
            credentialRepository,
            sessionRepository,
            passwordHasher,
            sessionTokenManager,
            loginAttemptLimiter,
            registrationAttemptLimiter,
            new AuthenticationPolicy(
                Duration.ofMinutes(30),
                Duration.ofMinutes(5),
                Duration.ofMinutes(2),
                5,
                Duration.ofMinutes(15)
            ),
            Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void register_savesUserAndCredential_whenEmailIsAvailable() {
        when(registrationAttemptLimiter.tryAcquire(SOURCE_ADDRESS, NOW)).thenReturn(true);
        when(passwordHasher.hash(PASSWORD)).thenReturn("encoded-password");
        when(userRepository.saveIfEmailAvailable(any(User.class)))
            .thenReturn(Optional.of(AuthenticationFixtures.activeUser()));

        authenticationService.register(new RegisterCommand(" Finance User ", EMAIL, PASSWORD, SOURCE_ADDRESS));

        var userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveIfEmailAvailable(userCaptor.capture());
        assertThat(userCaptor.getValue().getName()).isEqualTo("Finance User");
        assertThat(userCaptor.getValue().getNormalizedEmail()).isEqualTo(NORMALIZED_EMAIL);
        var credentialCaptor = ArgumentCaptor.forClass(Credential.class);
        verify(credentialRepository).save(credentialCaptor.capture());
        assertThat(credentialCaptor.getValue().getUserId()).isEqualTo(AuthenticationFixtures.USER_ID);
    }

    @Test
    void register_returnsWithoutCreatingCredential_whenEmailAlreadyExists() {
        when(registrationAttemptLimiter.tryAcquire(SOURCE_ADDRESS, NOW)).thenReturn(true);
        when(passwordHasher.hash(PASSWORD)).thenReturn("encoded-password");
        when(userRepository.saveIfEmailAvailable(any(User.class))).thenReturn(Optional.empty());

        authenticationService.register(new RegisterCommand("Finance User", EMAIL, PASSWORD, SOURCE_ADDRESS));

        verify(passwordHasher).hash(PASSWORD);
        verify(credentialRepository, never()).save(any());
    }

    @Test
    void register_throwsRateLimitExceeded_whenSourceIsBlocked() {
        when(registrationAttemptLimiter.tryAcquire(SOURCE_ADDRESS, NOW)).thenReturn(false);

        assertThatThrownBy(() -> authenticationService.register(
            new RegisterCommand("Finance User", EMAIL, PASSWORD, SOURCE_ADDRESS)
        )).isInstanceOf(RegistrationRateLimitExceededException.class);

        verify(passwordHasher, never()).hash(any());
        verify(userRepository, never()).saveIfEmailAvailable(any());
    }

    @Test
    void login_returnsIssuedSession_whenCredentialsAreValid() {
        var user = AuthenticationFixtures.activeUser();
        var credential = AuthenticationFixtures.credential();
        when(userRepository.findByNormalizedEmail(NORMALIZED_EMAIL)).thenReturn(Optional.of(user));
        when(credentialRepository.findByUserIdForUpdate(user.getId())).thenReturn(Optional.of(credential));
        when(passwordHasher.matches(PASSWORD, credential.getPasswordHash())).thenReturn(true);
        when(sessionTokenManager.generate()).thenReturn(RAW_TOKEN);
        when(sessionTokenManager.hash(RAW_TOKEN)).thenReturn(HASHED_TOKEN);

        var result = authenticationService.login(new LoginCommand(EMAIL, PASSWORD, SOURCE_ADDRESS));

        assertThat(result.rawToken()).isEqualTo(RAW_TOKEN);
        assertThat(result.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(30)));
        verify(loginAttemptLimiter).registerSuccess(NORMALIZED_EMAIL);
        verify(sessionRepository).save(any(Session.class));
    }

    @Test
    void login_throwsInvalidCredentials_whenPasswordIsIncorrect() {
        var user = AuthenticationFixtures.activeUser();
        var credential = AuthenticationFixtures.credential();
        when(userRepository.findByNormalizedEmail(NORMALIZED_EMAIL)).thenReturn(Optional.of(user));
        when(credentialRepository.findByUserIdForUpdate(user.getId())).thenReturn(Optional.of(credential));
        when(passwordHasher.matches(PASSWORD, credential.getPasswordHash())).thenReturn(false);

        assertThatThrownBy(() -> authenticationService.login(
            new LoginCommand(EMAIL, PASSWORD, SOURCE_ADDRESS)
        )).isInstanceOf(InvalidCredentialsException.class);

        assertThat(credential.getFailedAttemptCount()).isEqualTo(1);
        verify(credentialRepository).save(credential);
        verify(loginAttemptLimiter).registerFailure(NORMALIZED_EMAIL, SOURCE_ADDRESS, NOW);
        verify(sessionRepository, never()).save(any());
    }

    @Test
    void login_performsDummyVerification_whenUserDoesNotExist() {
        when(userRepository.findByNormalizedEmail(NORMALIZED_EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authenticationService.login(
            new LoginCommand(EMAIL, PASSWORD, SOURCE_ADDRESS)
        )).isInstanceOf(InvalidCredentialsException.class);

        verify(passwordHasher).performDummyVerification(PASSWORD);
        verify(loginAttemptLimiter).registerFailure(NORMALIZED_EMAIL, SOURCE_ADDRESS, NOW);
        verify(credentialRepository, never()).findByUserIdForUpdate(any());
    }

    @Test
    void login_performsDummyVerification_whenCredentialIsLocked() {
        var user = AuthenticationFixtures.activeUser();
        var credential = AuthenticationFixtures.lockedCredential();
        when(userRepository.findByNormalizedEmail(NORMALIZED_EMAIL)).thenReturn(Optional.of(user));
        when(credentialRepository.findByUserIdForUpdate(user.getId())).thenReturn(Optional.of(credential));

        assertThatThrownBy(() -> authenticationService.login(
            new LoginCommand(EMAIL, PASSWORD, SOURCE_ADDRESS)
        )).isInstanceOf(InvalidCredentialsException.class);

        verify(passwordHasher).performDummyVerification(PASSWORD);
        verify(passwordHasher, never()).matches(any(), any());
        verify(loginAttemptLimiter).registerFailure(NORMALIZED_EMAIL, SOURCE_ADDRESS, NOW);
    }

    @Test
    void login_throwsRateLimitExceeded_whenAttemptIsBlocked() {
        when(loginAttemptLimiter.isBlocked(NORMALIZED_EMAIL, SOURCE_ADDRESS, NOW)).thenReturn(true);

        assertThatThrownBy(() -> authenticationService.login(
            new LoginCommand(EMAIL, PASSWORD, SOURCE_ADDRESS)
        )).isInstanceOf(LoginRateLimitExceededException.class);

        verify(userRepository, never()).findByNormalizedEmail(any());
        verify(passwordHasher, never()).matches(any(), any());
    }

    @Test
    void logout_revokesSession_whenTokenIsUsable() {
        when(sessionTokenManager.hash(RAW_TOKEN)).thenReturn(HASHED_TOKEN);

        authenticationService.logout(RAW_TOKEN);

        verify(sessionRepository).revokeByTokenHash(HASHED_TOKEN, NOW);
    }

    @Test
    void authenticate_returnsUserWithoutRecordingActivity_whenUpdateIntervalHasNotElapsed() {
        var session = AuthenticationFixtures.validSession();
        when(sessionTokenManager.hash(RAW_TOKEN)).thenReturn(HASHED_TOKEN);
        when(sessionRepository.findByTokenHashForAuthentication(HASHED_TOKEN)).thenReturn(Optional.of(session));
        when(userRepository.findById(AuthenticationFixtures.USER_ID))
            .thenReturn(Optional.of(AuthenticationFixtures.activeUser()));

        var result = authenticationService.authenticate(RAW_TOKEN);

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().email()).isEqualTo(NORMALIZED_EMAIL);
        verify(sessionRepository, never()).recordActivity(any(), any());
    }

    @Test
    void authenticate_recordsActivity_whenUpdateIntervalHasElapsed() {
        var session = AuthenticationFixtures.sessionWithActivityUpdateDue();
        when(sessionTokenManager.hash(RAW_TOKEN)).thenReturn(HASHED_TOKEN);
        when(sessionRepository.findByTokenHashForAuthentication(HASHED_TOKEN)).thenReturn(Optional.of(session));
        when(userRepository.findById(AuthenticationFixtures.USER_ID))
            .thenReturn(Optional.of(AuthenticationFixtures.activeUser()));

        var result = authenticationService.authenticate(RAW_TOKEN);

        assertThat(result).isPresent();
        verify(sessionRepository).recordActivity(session, NOW);
    }

    @Test
    void authenticate_returnsEmpty_whenSessionIsExpired() {
        when(sessionTokenManager.hash(RAW_TOKEN)).thenReturn(HASHED_TOKEN);
        when(sessionRepository.findByTokenHashForAuthentication(HASHED_TOKEN))
            .thenReturn(Optional.of(AuthenticationFixtures.expiredSession()));

        var result = authenticationService.authenticate(RAW_TOKEN);

        assertThat(result).isEmpty();
        verify(userRepository, never()).findById(any());
    }

    @Test
    void authenticate_returnsEmpty_whenSessionIsIdle() {
        when(sessionTokenManager.hash(RAW_TOKEN)).thenReturn(HASHED_TOKEN);
        when(sessionRepository.findByTokenHashForAuthentication(HASHED_TOKEN))
            .thenReturn(Optional.of(AuthenticationFixtures.idleSession()));

        var result = authenticationService.authenticate(RAW_TOKEN);

        assertThat(result).isEmpty();
        verify(userRepository, never()).findById(any());
        verify(sessionRepository, never()).recordActivity(any(), any());
    }

    @Test
    void authenticate_returnsEmpty_whenSessionIsRevoked() {
        when(sessionTokenManager.hash(RAW_TOKEN)).thenReturn(HASHED_TOKEN);
        when(sessionRepository.findByTokenHashForAuthentication(HASHED_TOKEN))
            .thenReturn(Optional.of(AuthenticationFixtures.revokedSession()));

        var result = authenticationService.authenticate(RAW_TOKEN);

        assertThat(result).isEmpty();
        verify(userRepository, never()).findById(any());
    }
}
