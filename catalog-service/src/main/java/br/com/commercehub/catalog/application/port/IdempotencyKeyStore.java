package br.com.commercehub.catalog.application.port;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public interface IdempotencyKeyStore {

    /** Tenta inserir a chave (grava-primeiro). {@code true} = esta chamada ganhou a corrida; {@code false} = chave já existia. */
    boolean tryInsert(UUID idempotencyKey, String httpMethod, String resourceType,
                       OffsetDateTime createdAt, OffsetDateTime expiresAt);

    /**
     * Reivindica uma chave já existente cujo TTL expirou, devolvendo-a ao estado
     * "em processamento" (resourceId/responseStatus voltam a nulo). Só afeta a linha
     * se ela ainda estiver expirada, numa única operação atômica.
     *
     * @return {@code true} = esta chamada reivindicou a chave; {@code false} = outra
     *         requisição reivindicou primeiro, ou a chave não está mais expirada.
     */
    boolean tryClaimExpired(UUID idempotencyKey, OffsetDateTime createdAt,
                             OffsetDateTime expiresAt, OffsetDateTime now);

    /** Preenche resourceId/responseStatus depois que o recurso foi de fato criado. */
    void markResolved(UUID idempotencyKey, UUID resourceId, int responseStatus);

    Optional<IdempotencyKeyRecord> findByKey(UUID idempotencyKey);

    record IdempotencyKeyRecord(
        UUID idempotencyKey, String httpMethod, String resourceType,
        UUID resourceId, Integer responseStatus,
        OffsetDateTime createdAt, OffsetDateTime expiresAt
    ) {
    }
}
