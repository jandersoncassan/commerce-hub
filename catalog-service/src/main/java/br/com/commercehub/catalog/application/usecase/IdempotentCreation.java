package br.com.commercehub.catalog.application.usecase;

import br.com.commercehub.catalog.application.port.IdempotencyKeyStore;
import br.com.commercehub.catalog.application.port.IdempotencyKeyStore.IdempotencyKeyRecord;
import br.com.commercehub.catalog.domain.exception.DuplicateRequestInProgressException;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Criação de recurso deduplicada por {@code Idempotency-Key}, seguindo a estratégia
 * grava-primeiro da seção 8 do plan.md: a chave é inserida antes do recurso existir, e é o
 * próprio banco (PK única) que decide qual requisição concorrente ganha a corrida.
 *
 * <p>Existe uma única cópia deste fluxo, compartilhada por {@link CreateProductUseCase} e
 * {@link CreateCategoryUseCase} via {@link ResourceCreator} — a reivindicação da chave
 * expirada e o 409 em voo são sutis demais para viverem duplicados.
 *
 * <p>Contrapartida conhecida: {@link IdempotencyKeyStore#tryInsert} commita em transação
 * própria (REQUIRES_NEW), então se a criação do recurso falhar depois, a linha da chave
 * sobrevive com {@code resourceId} nulo e aquela chave passa a responder 409 até o TTL de
 * 24h expirar. É o preço de fechar a janela de duplicação — inserir a chave na mesma
 * transação do recurso reabriria o cenário "busca → não achou → processa" com dois recursos
 * criados.
 */
@Component
class IdempotentCreation {

    private static final String HTTP_METHOD = "POST";
    private static final int TTL_HOURS = 24;
    private static final int CREATED_STATUS = 201;

    private final IdempotencyKeyStore idempotencyKeyStore;

    IdempotentCreation(IdempotencyKeyStore idempotencyKeyStore) {
        this.idempotencyKeyStore = idempotencyKeyStore;
    }

    /**
     * @param idempotencyKey valor do header {@code Idempotency-Key}, ou {@code null} quando o
     *                       cliente não o enviou (sem deduplicação).
     */
    <T> CreationResult<T> execute(UUID idempotencyKey, ResourceCreator<T> creator) {
        if (idempotencyKey == null) {
            return CreationResult.created(creator.create(OffsetDateTime.now()));
        }

        OffsetDateTime now = OffsetDateTime.now();
        if (idempotencyKeyStore.tryInsert(idempotencyKey, HTTP_METHOD, creator.resourceType(), now, expiration(now))) {
            return CreationResult.created(createAndResolve(creator, idempotencyKey, now));
        }

        IdempotencyKeyRecord record = idempotencyKeyStore.findByKey(idempotencyKey)
            .orElseThrow(() -> inProgress(idempotencyKey));

        if (expired(record, now)) {
            return CreationResult.created(reprocessExpiredKey(creator, idempotencyKey, now));
        }
        if (record.resourceId() == null) {
            throw inProgress(idempotencyKey);
        }
        return CreationResult.existing(creator.findById(record.resourceId()));
    }

    /**
     * Chave expirada equivale a chave inexistente (o TTL desativa a deduplicação), mas a
     * linha ainda ocupa a PK — então é preciso reivindicá-la com um UPDATE condicional em vez
     * de reinserir. Perder a reivindicação significa que outra requisição chegou primeiro.
     */
    private <T> T reprocessExpiredKey(ResourceCreator<T> creator, UUID idempotencyKey, OffsetDateTime now) {
        if (!idempotencyKeyStore.tryClaimExpired(idempotencyKey, now, expiration(now), now)) {
            throw inProgress(idempotencyKey);
        }
        return createAndResolve(creator, idempotencyKey, now);
    }

    private <T> T createAndResolve(ResourceCreator<T> creator, UUID idempotencyKey, OffsetDateTime now) {
        T resource = creator.create(now);
        idempotencyKeyStore.markResolved(idempotencyKey, creator.idOf(resource), CREATED_STATUS);
        return resource;
    }

    private static boolean expired(IdempotencyKeyRecord record, OffsetDateTime now) {
        return !record.expiresAt().isAfter(now);
    }

    private static OffsetDateTime expiration(OffsetDateTime now) {
        return now.plusHours(TTL_HOURS);
    }

    private static DuplicateRequestInProgressException inProgress(UUID idempotencyKey) {
        return new DuplicateRequestInProgressException(
            "Requisição com a mesma Idempotency-Key já está em processamento: " + idempotencyKey);
    }
}
