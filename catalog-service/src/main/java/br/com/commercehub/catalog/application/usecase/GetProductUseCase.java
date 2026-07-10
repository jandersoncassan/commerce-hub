package br.com.commercehub.catalog.application.usecase;

import br.com.commercehub.catalog.application.port.ProductRepository;
import br.com.commercehub.catalog.domain.exception.ProductNotFoundException;
import br.com.commercehub.catalog.domain.model.Product;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * GET detail de produto. Produto inativo (soft-deleted) e produto inexistente são
 * indistinguíveis para o cliente: ambos resultam em 404. É o que dá efeito visível ao
 * DELETE soft do {@link DeactivateProductUseCase} — a linha continua no banco, mas some
 * da API.
 */
@Service
@Transactional(readOnly = true)
public class GetProductUseCase {

    private final ProductRepository productRepository;

    public GetProductUseCase(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public Product execute(UUID id) {
        return productRepository.findById(id)
            .filter(Product::active)
            .orElseThrow(() -> new ProductNotFoundException("Produto não encontrado: " + id));
    }
}
