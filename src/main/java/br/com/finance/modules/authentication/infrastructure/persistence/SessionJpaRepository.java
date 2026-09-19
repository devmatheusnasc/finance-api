package br.com.finance.modules.authentication.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

interface SessionJpaRepository extends JpaRepository<SessionJpaEntity, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select session
          from SessionJpaEntity session
         where session.tokenHash = :tokenHash
        """)
    Optional<SessionJpaEntity> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        update SessionJpaEntity session
           set session.lastActivityAt = :occurredAt
         where session.id = :sessionId
           and session.lastActivityAt = :expectedLastActivityAt
           and :occurredAt >= session.lastActivityAt
           and session.expiresAt > :occurredAt
           and session.revokedAt is null
        """)
    int recordActivity(@Param("sessionId") Long sessionId,
                       @Param("expectedLastActivityAt") Instant expectedLastActivityAt,
                       @Param("occurredAt") Instant occurredAt);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        update SessionJpaEntity session
           set session.revokedAt = :revokedAt
         where session.tokenHash = :tokenHash
           and session.revokedAt is null
        """)
    void revokeByTokenHash(@Param("tokenHash") String tokenHash, @Param("revokedAt") Instant revokedAt);
}
