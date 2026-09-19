package br.com.finance.modules.comum.exception;

public class LoginRateLimitExceededException extends RuntimeException {

    public LoginRateLimitExceededException() {
        super("Login rate limit exceeded");
    }
}
