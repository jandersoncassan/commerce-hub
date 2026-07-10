package br.com.commercehub.catalog.application.usecase;

import br.com.commercehub.catalog.application.port.CategoryRepository;
import br.com.commercehub.catalog.application.port.ProductRepository;
import br.com.commercehub.catalog.domain.exception.ProductNotFoundException;
import br.com.commercehub.catalog.domain.model.Product;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Criação de produto. A validação de preço/categoria é o que este usecase tem de próprio;
 * a deduplicação por {@code Idempotency-Key} fica em {@link IdempotentCreation}.
 */
@Service
@Transactional
public class CreateProductUseCase {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final IdempotentCreation idempotentCreation;

    CreateProductUseCase(ProductRepository productRepository, CategoryRepository categoryRepository,
                          IdempotentCreation idempotentCreation) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.idempotentCreation = idempotentCreation;
    }

    /**
     * A validação roda antes da idempotência: um payload inválido deve ser rejeitado sem
     * consumir a {@code Idempotency-Key} do cliente.
     *
     * @param idempotencyKey valor do header {@code Idempotency-Key}, ou {@code null}
     *                       quando o cliente não o enviou (sem deduplicação).
     */
    public CreationResult<Product> execute(CreateProductCommand command, UUID idempotencyKey) {
        ProductValidation.validate(command.price(), command.categoryId(), categoryRepository);

        return idempotentCreation.execute(idempotencyKey, new ResourceCreator<Product>() {

            @Override
            public String resourceType() {
                return "PRODUCT";
            }

            @Override
            public Product create(OffsetDateTime now) {
                return productRepository.save(new Product(
                    UUID.randomUUID(), command.name(), command.description(), command.price(),
                    command.categoryId(), true, now, now, 0L
                ));
            }

            @Override
            public UUID idOf(Product product) {
                return product.id();
            }

            @Override
            public Product findById(UUID id) {
                return productRepository.findById(id)
                    .orElseThrow(() -> new ProductNotFoundException("Produto não encontrado: " + id));
            }
        });
    }
}
