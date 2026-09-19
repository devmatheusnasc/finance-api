package br.com.finance.modules.comum.enums;

public enum ApplicationRole {

    ADMIN,
    USER;

    public String authority() {
        return "ROLE_" + name();
    }
}