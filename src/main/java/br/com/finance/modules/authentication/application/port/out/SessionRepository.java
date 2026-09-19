package br.com.finance.modules.authentication.application.port.out;

import br.com.finance.modules.authentication.domain.model.Session;

import java.time.Instant;
import java.util.Optional;

public interface SessionRepository {

    void save(Session session);

    Optional<Session> findByTokenHashForAuthentication(String tokenHash);

    void recordActivity(Session session, Instant occurredAt);

    void revokeByTokenHash(String tokenHash, Instant revokedAt);
}
