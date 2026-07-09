package br.com.commercehub.catalog.adapter.persistence;

import br.com.commercehub.catalog.application.port.IdempotencyKeyStore;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Component
public class IdempotencyKeyStoreAdapter implements IdempotencyKeyStore {

    private final IdempotencyKeyJpaRepository jpaRepository;
    private final TransactionTemplate requiresNewTransaction;

    public IdempotencyKeyStoreAdapter(IdempotencyKeyJpaRepository jpaRepository, PlatformTransactionManager transactionManager) {
        this.jpaRepository = jpaRepository;
        this.requiresNewTransaction = new TransactionTemplate(transactionManager);
        this.requiresNewTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * Transação própria (REQUIRES_NEW) via TransactionTemplate, não
     * {@code @Transactional} no próprio método: a spec JPA exige que, assim
     * que um flush falha, a transação atual seja marcada rollback-only —
     * então capturar a exceção DENTRO de um método {@code @Transactional}
     * não evita o UnexpectedRollbackException no commit seguinte, mesmo com
     * REQUIRES_NEW (a transação nova também fica poison a partir do próprio
     * flush que falhou). TransactionTemplate faz rollback explícito (não
     * commit) quando a exceção escapa do callback, sem esse problema — e
     * ainda isola a tentativa da transação de quem chama (ex.: o usecase de
     * criação).
     */
    @Override
    public boolean tryInsert(UUID idempotencyKey, String httpMethod, String resourceType,
                              OffsetDateTime createdAt, OffsetDateTime expiresAt) {
        try {
            requiresNewTransaction.executeWithoutResult(status ->
                jpaRepository.saveAndFlush(
                    new IdempotencyKeyEntity(idempotencyKey, httpMethod, resourceType, null, null, createdAt, expiresAt)
                )
            );
            return true;
        } catch (DataIntegrityViolationException e) {
            return false;
        }
    }

    @Override
    @Transactional
    public void markResolved(UUID idempotencyKey, UUID resourceId, int responseStatus) {
        IdempotencyKeyEntity entity = jpaRepository.findById(idempotencyKey)
            .orElseThrow(() -> new IllegalStateException("idempotency key not found: " + idempotencyKey));
        entity.setResourceId(resourceId);
        entity.setResponseStatus((short) responseStatus);
    }

    @Override
    public Optional<IdempotencyKeyRecord> findByKey(UUID idempotencyKey) {
        return jpaRepository.findById(idempotencyKey).map(IdempotencyKeyStoreAdapter::toRecord);
    }

    private static IdempotencyKeyRecord toRecord(IdempotencyKeyEntity entity) {
        Integer responseStatus = entity.getResponseStatus() == null ? null : entity.getResponseStatus().intValue();
        return new IdempotencyKeyRecord(
            entity.getIdempotencyKey(), entity.getHttpMethod(), entity.getResourceType(),
            entity.getResourceId(), responseStatus, entity.getCreatedAt(), entity.getExpiresAt()
        );
    }
}
