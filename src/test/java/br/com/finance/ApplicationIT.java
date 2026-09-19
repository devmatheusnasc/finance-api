package br.com.finance;

import br.com.finance.modules.authentication.infrastructure.security.AuthenticationSessionCookie;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@Testcontainers(disabledWithoutDocker = true)
class ApplicationIT {

    private static final String PASSWORD = UUID.randomUUID() + "!Aa1";
    private static final String INVALID_PASSWORD = UUID.randomUUID() + "!Bb2";
    private static final String CSRF_COOKIE_NAME = "__Host-FINANCE_CSRF";
    private static final String CSRF_HEADER_NAME = "X-CSRF-TOKEN";
    private static final String TEST_NETWORK_PREFIX = "198.51.100.";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRESQL = new PostgreSQLContainer<>("postgres:17-alpine");

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    ApplicationIT(MockMvc mockMvc,
                  ObjectMapper objectMapper,
                  JdbcTemplate jdbcTemplate,
                  TransactionTemplate transactionTemplate) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = transactionTemplate;
    }

    @Test
    @SneakyThrows
    void healthEndpoint_returnsOnlyStatus_whenApplicationStarts() {
        mockMvc.perform(get("/actuator/health"))
            .andExpect(status().isOk())
            .andExpect(header().exists("X-Correlation-ID"))
            .andExpect(header().string("X-Content-Type-Options", "nosniff"))
            .andExpect(jsonPath("$.status").value("UP"))
            .andExpect(jsonPath("$.components").doesNotExist())
            .andExpect(jsonPath("$.details").doesNotExist());
    }

    @Test
    @SneakyThrows
    void register_returnsNoContentAndStoresArgon2idHash_whenRequestIsValid() {
        var email = "register-success@finance.test";

        register(email, sourceAddress(10));

        var passwordHash = jdbcTemplate.queryForObject("""
            SELECT credential.password_hash
              FROM user_credential credential
              JOIN application_user app_user ON app_user.id = credential.user_id
             WHERE app_user.normalized_email = ?
            """, String.class, email);
        assertThat(passwordHash)
            .startsWith("$argon2id$")
            .doesNotContain(PASSWORD);
    }

    @Test
    @SneakyThrows
    void register_returnsValidationDetails_whenPasswordIsTooShort() {
        var csrf = csrf();
        var invalidPassword = "1".repeat(6);

        mockMvc.perform(withCsrf(post("/api/auth/register"), csrf)
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerJson("register-invalid@finance.test", invalidPassword)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Request validation failed"))
            .andExpect(jsonPath("$.violations[0].field").value("password"))
            .andExpect(jsonPath("$.violations[0].message")
                .value("Password must be between 12 and 128 characters"))
            .andExpect(content().string(not(containsString(invalidPassword))));
    }

    @Test
    @SneakyThrows
    void register_returnsSameNoContentResponseAndDoesNotDuplicate_whenEmailAlreadyExists() {
        var email = "register-duplicate@finance.test";

        register(email, sourceAddress(11));

        var csrf = csrf();
        mockMvc.perform(withCsrf(post("/api/auth/register"), csrf)
                .with(request -> {
                    request.setRemoteAddr(sourceAddress(12));
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerJson(email.toUpperCase())))
            .andExpect(status().isNoContent())
            .andExpect(content().string(""));

        var users = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM application_user WHERE normalized_email = ?",
            Integer.class,
            email
        );
        assertThat(users).isEqualTo(1);
    }

    @Test
    @SneakyThrows
    void register_returnsTooManyRequests_beforeExpensiveWork_whenSourceLimitIsExceeded() {
        var sourceAddress = sourceAddress(13);

        for (var attempt = 0; attempt < 5; attempt++) {
            register("registration-limit-" + attempt + "@finance.test", sourceAddress);
        }

        var csrf = csrf();
        mockMvc.perform(withCsrf(post("/api/auth/register"), csrf)
                .with(request -> {
                    request.setRemoteAddr(sourceAddress);
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerJson("registration-limit-blocked@finance.test")))
            .andExpect(status().isTooManyRequests())
            .andExpect(jsonPath("$.message").value("Too many authentication attempts"));

        var blockedUserCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM application_user WHERE normalized_email = ?",
            Integer.class,
            "registration-limit-blocked@finance.test"
        );
        assertThat(blockedUserCount).isZero();
    }

    @Test
    @SneakyThrows
    void login_returnsSecureSessionCookie_whenCredentialsAreValid() {
        var email = "login-success@finance.test";
        register(email, sourceAddress(20));
        var fixedSession = new Cookie(AuthenticationSessionCookie.NAME, "A".repeat(43));

        var result = login(email, PASSWORD, sourceAddress(21), fixedSession)
            .andExpect(status().isNoContent())
            .andExpect(cookie().httpOnly(AuthenticationSessionCookie.NAME, true))
            .andExpect(cookie().secure(AuthenticationSessionCookie.NAME, true))
            .andExpect(cookie().maxAge(CSRF_COOKIE_NAME, 0))
            .andReturn();

        var rawToken = sessionCookie(result).getValue();
        assertThat(rawToken).isNotEqualTo(fixedSession.getValue());
        assertThat(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE))
            .anyMatch(header -> header.startsWith(AuthenticationSessionCookie.NAME + "=")
                && header.contains("SameSite=Strict"))
            .noneMatch(header -> header.contains("Domain="));
        var storedTokenHash = jdbcTemplate.queryForObject("""
            SELECT session.token_hash
              FROM authentication_session session
              JOIN application_user app_user ON app_user.id = session.user_id
             WHERE app_user.normalized_email = ?
            """, String.class, email);
        assertThat(storedTokenHash)
            .hasSize(64)
            .isNotEqualTo(rawToken);
    }

    @Test
    @SneakyThrows
    void login_returnsSameUnauthorizedResponse_whenPasswordOrUserIsInvalid() {
        var email = "login-invalid@finance.test";
        register(email, sourceAddress(30));

        var wrongPassword = login(email, INVALID_PASSWORD, sourceAddress(31))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value("Invalid email or password"))
            .andExpect(content().string(not(containsString(email))))
            .andReturn();
        var missingUser = login("missing-user@finance.test", PASSWORD, sourceAddress(32))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value("Invalid email or password"))
            .andExpect(content().string(not(containsString("missing-user"))))
            .andReturn();

        assertThat(json(wrongPassword).get("status")).isEqualTo(json(missingUser).get("status"));
        assertThat(json(wrongPassword).get("message")).isEqualTo(json(missingUser).get("message"));
    }

    @Test
    @SneakyThrows
    void login_returnsTooManyRequests_whenAccountLimitIsExceeded() {
        var email = "login-rate-limit@finance.test";
        var sourceAddress = sourceAddress(40);
        register(email, sourceAddress);

        for (var attempt = 0; attempt < 5; attempt++) {
            login(email, INVALID_PASSWORD, sourceAddress)
                .andExpect(status().isUnauthorized());
        }

        login(email, PASSWORD, sourceAddress)
            .andExpect(status().isTooManyRequests())
            .andExpect(jsonPath("$.message").value("Too many authentication attempts"));

        var failedAttempts = jdbcTemplate.queryForObject("""
            SELECT credential.failed_attempt_count
              FROM user_credential credential
              JOIN application_user app_user ON app_user.id = credential.user_id
             WHERE app_user.normalized_email = ?
            """, Integer.class, email);
        assertThat(failedAttempts).isEqualTo(5);
    }

    @Test
    @SneakyThrows
    void login_enforcesSourceLimit_whenCredentialStuffingAttemptsAreConcurrent() {
        var sourceAddress = sourceAddress(41);
        var start = new CountDownLatch(1);
        var statuses = new ArrayBlockingQueue<Integer>(10);

        for (var account = 0; account < 10; account++) {
            register("stuffing-" + account + "@finance.test", sourceAddress(100 + account));
        }

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (var account = 0; account < 10; account++) {
                var email = "stuffing-" + account + "@finance.test";
                var csrf = csrf();

                executor.submit(() -> {
                    start.await();
                    var result = login(email, INVALID_PASSWORD, sourceAddress, csrf).andReturn();
                    statuses.add(result.getResponse().getStatus());
                    return null;
                });
            }

            start.countDown();
        }

        assertThat(statuses).hasSize(10);
        assertThat(statuses).filteredOn(status -> status == 401).hasSize(5);
        assertThat(statuses).filteredOn(status -> status == 429).hasSize(5);
    }

    @Test
    @SneakyThrows
    void me_returnsUnauthorized_whenSessionIsMissingOrInvalid() {
        mockMvc.perform(get("/api/auth/me"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value("Authentication required"));

        mockMvc.perform(get("/api/auth/me")
                .cookie(new Cookie(AuthenticationSessionCookie.NAME, "A".repeat(43))))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value("Authentication required"));
    }

    @Test
    @SneakyThrows
    void me_returnsCurrentUser_whenSessionIsValid() {
        var email = "protected-endpoint@finance.test";
        register(email, sourceAddress(50));
        var session = sessionCookie(login(email, PASSWORD, sourceAddress(51))
            .andExpect(status().isNoContent())
            .andReturn());

        mockMvc.perform(get("/api/auth/me").cookie(session))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
            .andExpect(jsonPath("$.id").isNumber())
            .andExpect(jsonPath("$.name").value("Finance User"))
            .andExpect(jsonPath("$.email").value(email));
    }

    @Test
    @SneakyThrows
    void me_coalescesSessionActivityWrites_whenSessionRemainsValid() {
        var email = "session-activity@finance.test";
        register(email, sourceAddress(52));
        var session = sessionCookie(login(email, PASSWORD, sourceAddress(53))
            .andExpect(status().isNoContent())
            .andReturn());
        var activityAtLogin = sessionActivity(email);

        mockMvc.perform(get("/api/auth/me").cookie(session))
            .andExpect(status().isOk());

        assertThat(sessionActivity(email)).isEqualTo(activityAtLogin);

        transactionTemplate.executeWithoutResult(status -> jdbcTemplate.update("""
                UPDATE authentication_session
                   SET created_at = NOW() - INTERVAL '10 minutes',
                       last_activity_at = NOW() - INTERVAL '2 minutes'
                 WHERE user_id = (
                     SELECT id FROM application_user WHERE normalized_email = ?
                 )
                """, email));
        var staleActivity = sessionActivity(email);

        mockMvc.perform(get("/api/auth/me").cookie(session))
            .andExpect(status().isOk());

        assertThat(sessionActivity(email)).isAfter(staleActivity);
    }

    @Test
    @SneakyThrows
    void logout_revokesSessionAndClearsCookie_whenRequested() {
        var email = "logout@finance.test";
        register(email, sourceAddress(60));
        var session = sessionCookie(login(email, PASSWORD, sourceAddress(61))
            .andExpect(status().isNoContent())
            .andReturn());
        var csrf = csrf();

        mockMvc.perform(withCsrf(post("/api/auth/logout"), csrf).cookie(session))
            .andExpect(status().isNoContent())
            .andExpect(cookie().maxAge(AuthenticationSessionCookie.NAME, 0))
            .andExpect(cookie().maxAge(CSRF_COOKIE_NAME, 0));

        mockMvc.perform(get("/api/auth/me").cookie(session))
            .andExpect(status().isUnauthorized());
        var activeSessions = jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
              FROM authentication_session session
              JOIN application_user app_user ON app_user.id = session.user_id
             WHERE app_user.normalized_email = ?
               AND session.revoked_at IS NULL
            """, Integer.class, email);
        assertThat(activeSessions).isZero();
    }

    @Test
    @SneakyThrows
    void me_returnsUnauthorized_whenSessionIsExpired() {
        var email = "expired-session@finance.test";

        register(email, sourceAddress(70));

        var session = sessionCookie(login(email, PASSWORD, sourceAddress(71))
            .andExpect(status().isNoContent())
            .andReturn());

        transactionTemplate.executeWithoutResult(status ->
            jdbcTemplate.update("""
                UPDATE authentication_session
                   SET created_at = NOW() - INTERVAL '1 hour',
                       last_activity_at = NOW() - INTERVAL '1 minute',
                       expires_at = NOW() - INTERVAL '1 second'
                 WHERE user_id = (
                     SELECT id
                       FROM application_user
                      WHERE normalized_email = ?
                 )
                """, email)
        );

        mockMvc.perform(get("/api/auth/me")
                .cookie(session))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value("Authentication required"));
    }

    @Test
    @SneakyThrows
    void me_returnsUnauthorized_whenSessionIdleTimeoutIsExceeded() {
        var email = "idle-session@finance.test";
        register(email, sourceAddress(72));
        var session = sessionCookie(login(email, PASSWORD, sourceAddress(73))
            .andExpect(status().isNoContent())
            .andReturn());
        transactionTemplate.executeWithoutResult(status -> jdbcTemplate.update("""
                UPDATE authentication_session
                   SET created_at = NOW() - INTERVAL '1 hour',
                       last_activity_at = NOW() - INTERVAL '6 minutes'
                 WHERE user_id = (
                     SELECT id FROM application_user WHERE normalized_email = ?
                 )
                """, email));

        mockMvc.perform(get("/api/auth/me").cookie(session))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value("Authentication required"));
    }

    @Test
    @SneakyThrows
    void login_returnsForbidden_whenCsrfTokenIsMissing() {
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginJson("csrf@finance.test", PASSWORD)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.message").value("Access denied"));
    }

    @Test
    @SneakyThrows
    void login_allowsOnlyConfiguredCorsOrigin_whenPreflightIsRequested() {
        mockMvc.perform(options("/api/auth/login")
                .header(HttpHeaders.ORIGIN, "https://app.finance.test")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "content-type,x-csrf-token"))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://app.finance.test"))
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));

        mockMvc.perform(options("/api/auth/login")
                .header(HttpHeaders.ORIGIN, "https://malicious.example")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
            .andExpect(status().isForbidden())
            .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @SneakyThrows
    private void register(String email, String sourceAddress) {
        var csrf = csrf();
        mockMvc.perform(withCsrf(post("/api/auth/register"), csrf)
                .with(request -> {
                    request.setRemoteAddr(sourceAddress);
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerJson(email)))
            .andExpect(status().isNoContent())
            .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
            .andExpect(content().string(""));
    }

    @SneakyThrows
    private org.springframework.test.web.servlet.ResultActions login(String email,
                                                                      String password,
                                                                      String sourceAddress) {
        var csrf = csrf();
        return login(email, password, sourceAddress, csrf);
    }

    @SneakyThrows
    private org.springframework.test.web.servlet.ResultActions login(String email,
                                                                      String password,
                                                                      String sourceAddress,
                                                                      Cookie session) {
        var csrf = csrf();
        return login(email, password, sourceAddress, csrf, session);
    }

    @SneakyThrows
    private org.springframework.test.web.servlet.ResultActions login(String email,
                                                                      String password,
                                                                      String sourceAddress,
                                                                      CsrfContext csrf,
                                                                      Cookie... cookies) {

        var request = withCsrf(post("/api/auth/login"), csrf)
            .with(servletRequest -> {
                servletRequest.setRemoteAddr(sourceAddress);
                return servletRequest;
            })
            .contentType(MediaType.APPLICATION_JSON)
            .content(loginJson(email, password));

        if (cookies.length > 0) {
            request.cookie(cookies);
        }

        return mockMvc.perform(request);
    }

    @SneakyThrows
    private CsrfContext csrf() {
        var result = mockMvc.perform(get("/api/auth/csrf"))
            .andExpect(status().isOk())
            .andExpect(cookie().exists(CSRF_COOKIE_NAME))
            .andReturn();
        var body = json(result);
        var cookie = Objects.requireNonNull(result.getResponse().getCookie(CSRF_COOKIE_NAME));
        assertThat(body.get("token").asText()).isNotEqualTo(cookie.getValue());

        return new CsrfContext(
            body.get("token").asText(),
            cookie
        );
    }

    private MockHttpServletRequestBuilder withCsrf(MockHttpServletRequestBuilder request, CsrfContext csrf) {
        return request.cookie(csrf.cookie).header(CSRF_HEADER_NAME, csrf.token);
    }

    private Cookie sessionCookie(MvcResult result) {
        return Objects.requireNonNull(result.getResponse().getCookie(AuthenticationSessionCookie.NAME));
    }

    private OffsetDateTime sessionActivity(String email) {
        return jdbcTemplate.queryForObject("""
            SELECT session.last_activity_at
              FROM authentication_session session
              JOIN application_user app_user ON app_user.id = session.user_id
             WHERE app_user.normalized_email = ?
            """, OffsetDateTime.class, email);
    }

    @SneakyThrows
    private JsonNode json(MvcResult result) {
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }

    @SneakyThrows
    private String registerJson(String email) {
        return registerJson(email, PASSWORD);
    }

    @SneakyThrows
    private String registerJson(String email, String password) {
        return objectMapper.writeValueAsString(new RegisterPayload("Finance User", email, password));
    }

    @SneakyThrows
    private String loginJson(String email, String password) {
        return objectMapper.writeValueAsString(new LoginPayload(email, password));
    }

    private String sourceAddress(int host) {
        return TEST_NETWORK_PREFIX + host;
    }

    @DynamicPropertySource
    static void testProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRESQL::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRESQL::getUsername);
        registry.add("spring.datasource.password", POSTGRESQL::getPassword);
        registry.add(
            "finance.security.authentication.rate-limit-key-secret",
            () -> UUID.randomUUID().toString()
        );
    }

    private static final class CsrfContext {

        private final String token;
        private final Cookie cookie;

        private CsrfContext(String token, Cookie cookie) {
            this.token = token;
            this.cookie = cookie;
        }
    }

    private record RegisterPayload(String name, String email, String password) {
    }

    private record LoginPayload(String email, String password) {
    }
}
