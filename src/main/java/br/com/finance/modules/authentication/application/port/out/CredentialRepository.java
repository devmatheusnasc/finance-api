package br.com.finance.modules.authentication.application.port.out;

import br.com.finance.modules.authentication.domain.model.Credential;

import java.util.Optional;

public interface CredentialRepository {

    void save(Credential credential);

    Optional<Credential> findByUserIdForUpdate(Long userId);
}
