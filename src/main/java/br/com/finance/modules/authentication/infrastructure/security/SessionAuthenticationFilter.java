package br.com.finance.modules.authentication.infrastructure.security;

import br.com.finance.config.security.HttpErrorResponseWriter;
import br.com.finance.modules.authentication.application.port.in.AuthenticationUseCase;
import br.com.finance.modules.authentication.application.result.AuthenticatedUser;
import br.com.finance.modules.comum.enums.ApplicationRole;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;

@Slf4j
@RequiredArgsConstructor
public class SessionAuthenticationFilter extends OncePerRequestFilter {

    private static final String USER_ROLE = "ROLE_USER";

    private final AuthenticationUseCase authenticationUseCase;
    private final AuthenticationSessionCookie sessionCookie;
    private final HttpErrorResponseWriter httpErrorResponseWriter;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {

        try {
            authenticate(request);
        } catch (RuntimeException ex) {
            handleAuthenticationFailure(request, response, ex);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void authenticate(HttpServletRequest request) {
        if (isAlreadyAuthenticated()) {
            return;
        }

        sessionCookie.read(request)
            .flatMap(authenticationUseCase::authenticate)
            .ifPresent(this::setAuthentication);
    }

    private boolean isAlreadyAuthenticated() {
        return SecurityContextHolder.getContext().getAuthentication() != null;
    }

    private void setAuthentication(AuthenticatedUser authenticatedUser) {
        var authentication = UsernamePasswordAuthenticationToken
                .authenticated(authenticatedUser, null,
                    List.of(new SimpleGrantedAuthority(ApplicationRole.USER.authority())));

        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);

        SecurityContextHolder.setContext(context);
    }

    private void handleAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                             RuntimeException ex) throws IOException {

        SecurityContextHolder.clearContext();

        log.atError()
            .addKeyValue("exceptionType", ex.getClass().getName())
            .log("Session authentication failed unexpectedly");

        httpErrorResponseWriter.write(request, response,
                SERVICE_UNAVAILABLE, "Authentication service is temporarily unavailable");
    }
}
