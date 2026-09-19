package br.com.finance.modules.authentication.infrastructure.presentation.web;

import br.com.finance.modules.authentication.application.command.LoginCommand;
import br.com.finance.modules.authentication.application.command.RegisterCommand;
import br.com.finance.modules.authentication.application.port.in.AuthenticationUseCase;
import br.com.finance.modules.authentication.application.result.AuthenticatedUser;
import br.com.finance.modules.authentication.infrastructure.presentation.web.request.LoginRequest;
import br.com.finance.modules.authentication.infrastructure.presentation.web.request.RegisterRequest;
import br.com.finance.modules.authentication.infrastructure.presentation.web.response.AuthenticatedUserResponse;
import br.com.finance.modules.authentication.infrastructure.presentation.web.response.CsrfTokenResponse;
import br.com.finance.modules.authentication.infrastructure.security.AuthenticationSessionCookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.http.HttpHeaders.CACHE_CONTROL;
import static org.springframework.http.HttpHeaders.SET_COOKIE;
import static org.springframework.http.HttpStatus.NO_CONTENT;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthenticationController {

    private static final String NO_STORE = "no-store";

    private final AuthenticationUseCase authenticationUseCase;
    private final AuthenticationSessionCookie sessionCookie;
    private final CsrfTokenRepository csrfTokenRepository;

    @PostMapping("/register")
    @ResponseStatus(NO_CONTENT)
    public void register(@Valid @RequestBody RegisterRequest request, HttpServletRequest servletRequest) {
        authenticationUseCase.register(new RegisterCommand(
            request.name(),
            request.email(),
            request.password(),
            servletRequest.getRemoteAddr()
        ));
    }

    @PostMapping("/login")
    public ResponseEntity<Void> login(@Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest,
                                      HttpServletResponse servletResponse) {

        var issuedSession = authenticationUseCase.login(
            new LoginCommand(request.email(), request.password(), servletRequest.getRemoteAddr()));

        csrfTokenRepository.saveToken(null, servletRequest, servletResponse);

        return ResponseEntity.noContent()
            .header(SET_COOKIE, sessionCookie.create(issuedSession))
            .header(CACHE_CONTROL, NO_STORE)
            .build();
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        authenticationUseCase.logout(sessionCookie.read(request).orElse(null));
        csrfTokenRepository.saveToken(null, request, response);

        return ResponseEntity.noContent()
            .header(SET_COOKIE, sessionCookie.clear())
            .header(CACHE_CONTROL, NO_STORE)
            .build();
    }

    @GetMapping("/me")
    public ResponseEntity<AuthenticatedUserResponse> me(@AuthenticationPrincipal AuthenticatedUser authenticatedUser) {
        return ResponseEntity.ok()
            .header(CACHE_CONTROL, NO_STORE)
            .body(AuthenticatedUserResponse.of(authenticatedUser));
    }

    @GetMapping("/csrf")
    public ResponseEntity<CsrfTokenResponse> csrf(CsrfToken csrfToken) {
        return ResponseEntity.ok()
            .header(CACHE_CONTROL, NO_STORE)
            .body(new CsrfTokenResponse(csrfToken.getToken()));
    }
}
