package br.com.finance.config.security;

import br.com.finance.modules.authentication.application.port.in.AuthenticationUseCase;
import br.com.finance.modules.authentication.infrastructure.security.AuthenticationSessionCookie;
import br.com.finance.modules.authentication.infrastructure.security.SessionAuthenticationFilter;
import br.com.finance.modules.comum.enums.ApplicationRole;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer.FrameOptionsConfig;
import org.springframework.security.config.annotation.web.configurers.RequestCacheConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;

import java.util.List;

import static org.springframework.http.HttpHeaders.ACCEPT;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;
import static org.springframework.security.config.http.SessionCreationPolicy.STATELESS;
import static org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER;

@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
@EnableConfigurationProperties(CorsProperties.class)
public class SecurityConfig {

    private static final String CSRF_HEADER = "X-CSRF-TOKEN";
    private static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
    private static final String[] PUBLIC_ENDPOINTS = {
        "/actuator/health",
        "/actuator/health/**"
    };
    private static final String[] PUBLIC_AUTHENTICATION_ENDPOINTS = {
        "/api/auth/register",
        "/api/auth/login",
        "/api/auth/logout"
    };

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            SessionAuthenticationFilter sessionAuthenticationFilter,
                                            HttpErrorResponseWriter httpErrorResponseWriter,
                                            CookieCsrfTokenRepository csrfTokenRepository,
                                            CorsConfigurationSource corsConfigurationSource) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource))
            .csrf(csrf -> csrf.csrfTokenRepository(csrfTokenRepository)
                .csrfTokenRequestHandler(new XorCsrfTokenRequestAttributeHandler()))
            .sessionManagement(session -> session.sessionCreationPolicy(STATELESS))
            .securityContext(context -> context.requireExplicitSave(true))
            .requestCache(RequestCacheConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .logout(AbstractHttpConfigurer::disable)

            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers(HttpMethod.GET, PUBLIC_ENDPOINTS).permitAll()
                .requestMatchers(HttpMethod.GET, "/api/auth/csrf").permitAll()
                .requestMatchers(HttpMethod.POST, PUBLIC_AUTHENTICATION_ENDPOINTS).permitAll()
                .requestMatchers(HttpMethod.GET, "/api/auth/me")
                .hasRole(ApplicationRole.USER.name())
                .anyRequest().denyAll())

            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint((request, response, exception) -> httpErrorResponseWriter.write(
                    request,
                    response,
                    UNAUTHORIZED,
                    "Authentication required"
                ))
                .accessDeniedHandler((request, response, exception) -> httpErrorResponseWriter.write(
                    request,
                    response,
                    FORBIDDEN,
                    "Access denied"
                )))

            .headers(headers -> headers
                .contentSecurityPolicy(csp -> csp.policyDirectives(
                    "default-src 'none'; frame-ancestors 'none'; form-action 'none'; base-uri 'none'"
                ))
                .frameOptions(FrameOptionsConfig::deny)
                .referrerPolicy(referrer -> referrer.policy(NO_REFERRER))
                .permissionsPolicyHeader(permissions -> permissions.policy(
                    "camera=(), microphone=(), geolocation=(), payment=()"
                ))
                .httpStrictTransportSecurity(hsts -> hsts
                    .includeSubDomains(true)
                    .preload(true)
                    .maxAgeInSeconds(31_536_000)))
            .addFilterBefore(sessionAuthenticationFilter, AnonymousAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    SessionAuthenticationFilter sessionAuthenticationFilter(AuthenticationUseCase authenticationUseCase,
                                                              AuthenticationSessionCookie sessionCookie,
                                                              HttpErrorResponseWriter httpErrorResponseWriter) {
        return new SessionAuthenticationFilter(authenticationUseCase, sessionCookie, httpErrorResponseWriter);
    }

    @Bean
    FilterRegistrationBean<SessionAuthenticationFilter> disableSessionAuthenticationFilterRegistration(
            SessionAuthenticationFilter filter) {

        var registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    CookieCsrfTokenRepository csrfTokenRepository() {
        var repository = new CookieCsrfTokenRepository();
        repository.setCookieName("__Host-FINANCE_CSRF");
        repository.setHeaderName(CSRF_HEADER);
        repository.setCookieCustomizer(cookie -> cookie
            .httpOnly(true)
            .secure(true)
            .sameSite("Strict")
            .path("/"));
        return repository;
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(CorsProperties properties) {
        var configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(properties.allowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
        configuration.setAllowedHeaders(List.of(
            ACCEPT,
            CONTENT_TYPE,
            CSRF_HEADER
        ));
        configuration.setExposedHeaders(List.of(CORRELATION_ID_HEADER));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(600L);

        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
