package br.com.commercehub.catalog.adapter.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.UUID;

interface IdempotencyKeyJpaRepository extends JpaRepository<IdempotencyKeyEntity, UUID> {

    /**
     * INSERT nativo, não {@code save()}: como {@code idempotencyKey} é atribuído manualmente
     * (sem {@code @GeneratedValue}), Spring Data não sabe se a entidade é "nova" e o {@code
     * save()} genérico cai em {@code merge()} — que faz SELECT+UPDATE quando já existe uma
     * linha com aquele id, em vez de tentar um INSERT que estouraria a constraint de
     * unicidade. Isso mascarava silenciosamente uma chave duplicada como sucesso (a segunda
     * chamada "reescrevia" a mesma linha em vez de falhar), quebrando a estratégia
     * grava-primeiro da seção 8 do plan.md em retries sequenciais com a mesma chave — só não
     * aparecia em inserts concorrentes de verdade, onde a corrida acontece antes de qualquer
     * SELECT enxergar a linha do outro. Um INSERT nativo força a constraint do banco a valer
     * sempre, sequencial ou concorrente.
     */
    @Modifying
    @Query(value = """
        insert into catalog.idempotency_keys
            (idempotency_key, http_method, resource_type, resource_id, response_status, created_at, expires_at)
        values (:key, :httpMethod, :resourceType, null, null, :createdAt, :expiresAt)
        """, nativeQuery = true)
    void insertNew(@Param("key") UUID key,
                   @Param("httpMethod") String httpMethod,
                   @Param("resourceType") String resourceType,
                   @Param("createdAt") OffsetDateTime createdAt,
                   @Param("expiresAt") OffsetDateTime expiresAt);

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
