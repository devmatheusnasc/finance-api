package br.com.finance.modules.comum.exception;

public class RegistrationRateLimitExceededException extends RuntimeException {

    public RegistrationRateLimitExceededException() {
        super("Registration rate limit exceeded");
    }
}
