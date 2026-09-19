package br.com.finance.modules.authentication.application.port.out;

public interface SessionTokenManager {

    String generate();

    String hash(String rawToken);

    boolean isInvalidFormat(String rawToken);
}