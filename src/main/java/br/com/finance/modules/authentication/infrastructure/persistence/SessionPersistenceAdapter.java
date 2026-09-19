package br.com.finance.modules.authentication.infrastructure.persistence;

import br.com.finance.modules.authentication.application.port.out.SessionRepository;
import br.com.finance.modules.authentication.domain.model.Session;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;

@Component
@RequiredArgsConstructor
class SessionPersistenceAdapter implements SessionRepository {

    private final SessionJpaRepository repository;

    @Override
    public void save(Session session) {
        repository.save(toEntity(session));
    }

    @Override
    public Optional<Session> findByTokenHashForAuthentication(String tokenHash) {
        return repository.findByTokenHashForUpdate(tokenHash)
            .map(SessionPersistenceAdapter::toDomain);
    }

    @Override
    public void recordActivity(Session session, Instant occurredAt) {
        var updatedRows = repository.recordActivity(session.getId(), session.getLastActivityAt(), occurredAt);

        if (updatedRows != 1) {
            throw new IllegalStateException("Session activity could not be updated");
        }
    }

    @Override
    public void revokeByTokenHash(String tokenHash, Instant revokedAt) {
        repository.revokeByTokenHash(tokenHash, revokedAt);
    }

    private static SessionJpaEntity toEntity(Session session) {
        return new SessionJpaEntity(
            session.getId(),
            session.getUserId(),
            session.getTokenHash(),
            session.getCreatedAt(),
            session.getLastActivityAt(),
            session.getExpiresAt(),
            session.getRevokedAt()
        );
    }

    private static Session toDomain(SessionJpaEntity entity) {
        return Session.restore(
            entity.getId(),
            entity.getUserId(),
            entity.getTokenHash(),
            entity.getCreatedAt(),
            entity.getLastActivityAt(),
            entity.getExpiresAt(),
            entity.getRevokedAt()
        );
    }
}
