package br.com.finance.modules.authentication.application;

import br.com.finance.modules.authentication.domain.model.Credential;
import br.com.finance.modules.authentication.domain.model.Session;
import br.com.finance.modules.authentication.domain.model.User;

import java.time.Instant;

final class AuthenticationFixtures {

    static final Long USER_ID = 42L;
    static final Instant NOW = Instant.parse("2026-09-18T12:00:00Z");

    private AuthenticationFixtures() {
    }

    static User activeUser() {
        return User.restore(
            USER_ID,
            "Finance User",
            "user@finance.test",
            "user@finance.test",
            true,
            NOW.minusSeconds(3_600),
            NOW.minusSeconds(3_600)
        );
    }

    static Credential credential() {
        return Credential.create(USER_ID, "encoded-password", NOW.minusSeconds(3_600));
    }

    static Credential lockedCredential() {
        return Credential.restore(
            USER_ID,
            "encoded-password",
            5,
            NOW.plusSeconds(900),
            NOW.minusSeconds(3_600)
        );
    }

    static Session validSession() {
        return Session.restore(
            101L,
            USER_ID,
            "hashed-token",
            NOW.minusSeconds(60),
            NOW.minusSeconds(60),
            NOW.plusSeconds(1_800),
            null
        );
    }

    static Session sessionWithActivityUpdateDue() {
        return Session.restore(
            105L,
            USER_ID,
            "activity-update-due-token-hash",
            NOW.minusSeconds(3_600),
            NOW.minusSeconds(180),
            NOW.plusSeconds(1_800),
            null
        );
    }

    static Session expiredSession() {
        return Session.restore(
            102L,
            USER_ID,
            "expired-token-hash",
            NOW.minusSeconds(3_600),
            NOW.minusSeconds(60),
            NOW.minusSeconds(1),
            null
        );
    }

    static Session idleSession() {
        return Session.restore(
            104L,
            USER_ID,
            "idle-token-hash",
            NOW.minusSeconds(3_600),
            NOW.minusSeconds(301),
            NOW.plusSeconds(1_800),
            null
        );
    }

    static Session revokedSession() {
        return Session.restore(
            103L,
            USER_ID,
            "revoked-token-hash",
            NOW.minusSeconds(3_600),
            NOW.minusSeconds(60),
            NOW.plusSeconds(1_800),
            NOW.minusSeconds(60)
        );
    }
}
