package br.com.finance.modules.authentication.infrastructure.config;

import br.com.finance.modules.authentication.application.policy.AuthenticationPolicy;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AuthenticationProperties.class)
public class AuthenticationModuleConfiguration {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    AuthenticationPolicy authenticationPolicy(AuthenticationProperties properties) {
        return new AuthenticationPolicy(
            properties.sessionDuration(),
            properties.sessionIdleTimeout(),
            properties.sessionActivityUpdateInterval(),
            properties.credentialMaxFailures(),
            properties.credentialLockDuration()
        );
    }
}
