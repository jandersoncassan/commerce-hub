package br.com.commercehub.catalog.application.usecase;

import br.com.commercehub.catalog.application.port.CategoryRepository;
import br.com.commercehub.catalog.application.port.IdempotencyKeyStore;
import br.com.commercehub.catalog.application.port.IdempotencyKeyStore.IdempotencyKeyRecord;
import br.com.commercehub.catalog.application.port.ProductRepository;
import br.com.commercehub.catalog.domain.exception.DuplicateRequestInProgressException;
import br.com.commercehub.catalog.domain.exception.ProductNotFoundException;
import br.com.commercehub.catalog.domain.model.Product;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Criação de produto com deduplicação por {@code Idempotency-Key}, seguindo a
 * estratégia grava-primeiro da seção 8 do plan.md: a chave é inserida antes do
 * recurso existir, e é o próprio banco (PK única) que decide qual requisição
 * concorrente ganha a corrida.
 *
 * <p>Contrapartida conhecida: {@link IdempotencyKeyStore#tryInsert} commita em
 * transação própria (REQUIRES_NEW), então se a criação do produto falhar depois,
 * a linha da chave sobrevive com {@code resourceId} nulo e aquela chave passa a
 * responder 409 até o TTL de 24h expirar. É o preço de fechar a janela de
 * duplicação — inserir a chave na mesma transação do produto reabriria o cenário
 * "busca → não achou → processa" com dois recursos criados.
 */
@Service
@Transactional
public class CreateProductUseCase {

    private static final String HTTP_METHOD = "POST";
    private static final String RESOURCE_TYPE = "PRODUCT";
    private static final int TTL_HOURS = 24;
    private static final int CREATED_STATUS = 201;

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final IdempotencyKeyStore idempotencyKeyStore;

    public CreateProductUseCase(ProductRepository productRepository, CategoryRepository categoryRepository,
                                 IdempotencyKeyStore idempotencyKeyStore) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.idempotencyKeyStore = idempotencyKeyStore;
    }

    /**
     * @param idempotencyKey valor do header {@code Idempotency-Key}, ou {@code null}
     *                       quando o cliente não o enviou (sem deduplicação).
     */
    public CreationResult<Product> execute(CreateProductCommand command, UUID idempotencyKey) {
        ProductValidation.validate(command.price(), command.categoryId(), categoryRepository);

        if (idempotencyKey == null) {
            return CreationResult.created(create(command, OffsetDateTime.now()));
        }

        OffsetDateTime now = OffsetDateTime.now();
        if (idempotencyKeyStore.tryInsert(idempotencyKey, HTTP_METHOD, RESOURCE_TYPE, now, expiration(now))) {
            return CreationResult.created(createAndResolve(command, idempotencyKey, now));
        }

        IdempotencyKeyRecord record = idempotencyKeyStore.findByKey(idempotencyKey)
            .orElseThrow(() -> inProgress(idempotencyKey));

        if (expired(record, now)) {
            return CreationResult.created(reprocessExpiredKey(command, idempotencyKey, now));
        }
        if (record.resourceId() == null) {
            throw inProgress(idempotencyKey);
        }
        return CreationResult.existing(findById(record.resourceId()));
    }

    /**
     * Chave expirada equivale a chave inexistente (o TTL desativa a deduplicação), mas a
     * linha ainda ocupa a PK — então é preciso reivindicá-la com um UPDATE condicional em vez
     * de reinserir. Perder a reivindicação significa que outra requisição chegou primeiro.
     */
    private Product reprocessExpiredKey(CreateProductCommand command, UUID idempotencyKey, OffsetDateTime now) {
        if (!idempotencyKeyStore.tryClaimExpired(idempotencyKey, now, expiration(now), now)) {
            throw inProgress(idempotencyKey);
        }
        return createAndResolve(command, idempotencyKey, now);
    }

    private Product createAndResolve(CreateProductCommand command, UUID idempotencyKey, OffsetDateTime now) {
        Product product = create(command, now);
        idempotencyKeyStore.markResolved(idempotencyKey, product.id(), CREATED_STATUS);
        return product;
    }

    private Product create(CreateProductCommand command, OffsetDateTime now) {
        return productRepository.save(new Product(
            UUID.randomUUID(), command.name(), command.description(), command.price(),
            command.categoryId(), true, now, now, 0L
        ));
    }

    private Product findById(UUID productId) {
        return productRepository.findById(productId)
            .orElseThrow(() -> new ProductNotFoundException("Produto não encontrado: " + productId));
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
