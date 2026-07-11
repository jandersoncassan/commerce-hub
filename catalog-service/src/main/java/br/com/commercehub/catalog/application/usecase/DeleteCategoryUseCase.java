package br.com.commercehub.catalog.application.usecase;

import br.com.commercehub.catalog.application.port.CategoryRepository;
import br.com.commercehub.catalog.application.port.ProductRepository;
import br.com.commercehub.catalog.domain.exception.CategoryHasActiveProductsException;
import br.com.commercehub.catalog.domain.exception.CategoryNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * DELETE físico de categoria: a linha some da tabela, diferente do DELETE soft de produto.
 * Só é permitido quando não há produto <strong>ativo</strong> vinculado — produtos inativos não
 * bloqueiam, já que somem das listagens e não têm mais relevância de negócio (seção 7 do
 * plan.md).
 *
 * <p>Produto inativo continua com uma FK {@code NOT NULL} para a categoria (V2 migration não
 * tem {@code ON DELETE CASCADE}), então o DELETE físico da categoria estouraria violação de
 * integridade referencial se essas linhas ainda existissem. Por isso este usecase apaga
 * fisicamente os produtos inativos da categoria antes de apagar a categoria em si, na mesma
 * transação — seguro porque um produto inativo já é invisível para a API (soft-deleted) e não
 * tem mais relevância de negócio.
 */
@Service
@Transactional
public class DeleteCategoryUseCase {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;

    public DeleteCategoryUseCase(CategoryRepository categoryRepository, ProductRepository productRepository) {
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
    }

    public void execute(UUID id) {
        if (!categoryRepository.existsById(id)) {
            throw new CategoryNotFoundException("Categoria não encontrada: " + id);
        }

        if (categoryRepository.countActiveProductsByCategory(id) > 0) {
            throw new CategoryHasActiveProductsException("Categoria possui produtos ativos vinculados: " + id);
        }

        productRepository.deleteInactiveByCategory(id);
        categoryRepository.deleteById(id);
    }
}
