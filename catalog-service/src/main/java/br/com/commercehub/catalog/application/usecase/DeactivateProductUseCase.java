package br.com.commercehub.catalog.application.usecase;

import br.com.commercehub.catalog.application.port.ProductRepository;
import br.com.commercehub.catalog.domain.exception.ProductNotFoundException;
import br.com.commercehub.catalog.domain.model.Product;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * DELETE soft de produto: a linha permanece na tabela e só {@code active} vira {@code false}.
 * Produtos inativos somem das listagens, mas continuam referenciáveis por {@code productId}
 * a partir de outros serviços (ADR 004).
 *
 * <p>Desativar um produto já inativo é um no-op de verdade — retorna antes de chegar ao
 * {@code save}. Como {@code ProductRepositoryAdapter.save} faz {@code saveAndFlush}, um
 * {@code save} incondicional emitiria um UPDATE e bumparia {@code updatedAt}/{@code version}
 * mesmo sem mudança de estado, quebrando a idempotência que o DELETE promete.
 */
@Service
@Transactional
public class DeactivateProductUseCase {

    private final ProductRepository productRepository;

    public DeactivateProductUseCase(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public void execute(UUID id) {
        Product existing = productRepository.findById(id)
            .orElseThrow(() -> new ProductNotFoundException("Produto não encontrado: " + id));

        if (!existing.active()) {
            return;
        }

        productRepository.save(new Product(
            existing.id(), existing.name(), existing.description(), existing.price(),
            existing.categoryId(), false, existing.createdAt(), OffsetDateTime.now(),
            existing.version()
        ));
    }
}
