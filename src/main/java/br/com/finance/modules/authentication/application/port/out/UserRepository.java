package br.com.finance.modules.authentication.application.port.out;

import br.com.finance.modules.authentication.domain.model.User;

import java.util.Optional;

public interface UserRepository {

    Optional<User> saveIfEmailAvailable(User user);

    Optional<User> findByNormalizedEmail(String normalizedEmail);

    Optional<User> findById(Long id);
}
