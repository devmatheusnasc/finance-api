package br.com.finance.modules.authentication.infrastructure.persistence;

import br.com.finance.modules.authentication.application.port.out.CredentialRepository;
import br.com.finance.modules.authentication.domain.model.Credential;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
class CredentialPersistenceAdapter implements CredentialRepository {

    private final CredentialJpaRepository repository;

    @Override
    public void save(Credential credential) {
        repository.save(toEntity(credential));
    }

    @Override
    public Optional<Credential> findByUserIdForUpdate(Long userId) {
        return repository.findByUserIdForUpdate(userId).map(CredentialPersistenceAdapter::toDomain);
    }

    private static CredentialJpaEntity toEntity(Credential credential) {
        return new CredentialJpaEntity(
            credential.getUserId(),
            credential.getPasswordHash(),
            credential.getFailedAttemptCount(),
            credential.getLockedUntil(),
            credential.getPasswordChangedAt()
        );
    }

    private static Credential toDomain(CredentialJpaEntity entity) {
        return Credential.restore(
            entity.getUserId(),
            entity.getPasswordHash(),
            entity.getFailedAttemptCount(),
            entity.getLockedUntil(),
            entity.getPasswordChangedAt()
        );
    }
}
