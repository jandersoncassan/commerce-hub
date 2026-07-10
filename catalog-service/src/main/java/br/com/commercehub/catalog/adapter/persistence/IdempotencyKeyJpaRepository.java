package br.com.commercehub.catalog.adapter.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.UUID;

interface IdempotencyKeyJpaRepository extends JpaRepository<IdempotencyKeyEntity, UUID> {

    @Modifying(clearAutomatically = true)
    @Query("""
        update IdempotencyKeyEntity e
           set e.createdAt = :createdAt, e.expiresAt = :expiresAt,
               e.resourceId = null, e.responseStatus = null
         where e.idempotencyKey = :key and e.expiresAt <= :now
        """)
    int claimExpired(@Param("key") UUID key,
                      @Param("createdAt") OffsetDateTime createdAt,
                      @Param("expiresAt") OffsetDateTime expiresAt,
                      @Param("now") OffsetDateTime now);
}
