package br.com.commercehub.catalog.application.port;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public interface IdempotencyKeyStore {

    /** Tenta inserir a chave (grava-primeiro). {@code true} = esta chamada ganhou a corrida; {@code false} = chave já existia. */
    boolean tryInsert(UUID idempotencyKey, String httpMethod, String resourceType,
                       OffsetDateTime createdAt, OffsetDateTime expiresAt);

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
