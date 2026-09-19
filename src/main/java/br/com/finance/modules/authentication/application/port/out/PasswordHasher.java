package br.com.finance.modules.authentication.application.port.out;

public interface PasswordHasher {

    String hash(String rawPassword);

    boolean matches(String rawPassword, String passwordHash);

    void performDummyVerification(String rawPassword);
}
