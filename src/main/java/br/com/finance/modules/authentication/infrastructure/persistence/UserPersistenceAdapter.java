package br.com.finance.modules.authentication.infrastructure.persistence;

import br.com.finance.modules.authentication.application.port.out.UserRepository;
import br.com.finance.modules.authentication.domain.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

@Component
@RequiredArgsConstructor
class UserPersistenceAdapter implements UserRepository {

    private final UserJpaRepository repository;
    private final NamedParameterJdbcTemplate jdbcTemplate;

    @Override
    public Optional<User> saveIfEmailAvailable(User user) {
        var ids = jdbcTemplate.queryForList(
                """
                INSERT INTO application_user (
                    name,
                    email,
                    normalized_email,
                    is_active,
                    created_at,
                    updated_at
                )
                VALUES (
                    :name,
                    :email,
                    :normalizedEmail,
                    :isActive,
                    :createdAt,
                    :updatedAt
                )
                ON CONFLICT (normalized_email) DO NOTHING
                RETURNING id
                """,
                parametersOf(user),
                Long.class
        );

        return ids.stream()
            .findFirst()
            .map(id -> restore(user, id));
    }

    @Override
    public Optional<User> findByNormalizedEmail(String normalizedEmail) {
        return repository.findByNormalizedEmail(normalizedEmail)
            .map(UserPersistenceAdapter::toDomain);
    }

    @Override
    public Optional<User> findById(Long id) {
        return repository.findById(id)
            .map(UserPersistenceAdapter::toDomain);
    }

    private static MapSqlParameterSource parametersOf(User user) {
        return new MapSqlParameterSource()
            .addValue("name", user.getName())
            .addValue("email", user.getEmail())
            .addValue("normalizedEmail", user.getNormalizedEmail())
            .addValue("isActive", user.isActive())
            .addValue("createdAt", toUtc(user.getCreatedAt()))
            .addValue("updatedAt", toUtc(user.getUpdatedAt()));
    }

    private static User restore(User user, Long id) {
        return User.restore(
            id,
            user.getName(),
            user.getEmail(),
            user.getNormalizedEmail(),
            user.isActive(),
            user.getCreatedAt(),
            user.getUpdatedAt()
        );
    }

    private static User toDomain(UserJpaEntity entity) {
        return User.restore(
            entity.getId(),
            entity.getName(),
            entity.getEmail(),
            entity.getNormalizedEmail(),
            entity.isActive(),
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }

    private static OffsetDateTime toUtc(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
