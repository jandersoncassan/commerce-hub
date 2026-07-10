package br.com.commercehub.catalog.application.usecase;

import br.com.commercehub.catalog.application.port.CategoryRepository;
import br.com.commercehub.catalog.application.port.ProductRepository;
import br.com.commercehub.catalog.domain.exception.ProductNotFoundException;
import br.com.commercehub.catalog.domain.model.Product;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

/**
 * Atualização total (PUT) de produto, com detecção de conflito por optimistic locking.
 *
 * <p>O {@code version} que chega no comando é o que o cliente leu no GET anterior, e é ele
 * — não o da linha atual — que precisa ir para o {@code save}. O produto montado aqui é
 * uma cópia <em>detached</em>: o Hibernate compara esse {@code version} contra o valor da
 * coluna no UPDATE e lança {@code ObjectOptimisticLockingFailureException} se divergirem.
 * Carregar a entidade managed e sobrescrever o {@code version} dela não funcionaria — ela
 * usa o valor que carregou do banco, e a atribuição posterior é ignorada (nota da seção 7
 * do plan.md).
 */
@Service
@Transactional
public class UpdateProductUseCase {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    public UpdateProductUseCase(ProductRepository productRepository, CategoryRepository categoryRepository) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
    }

    public Product execute(UpdateProductCommand command) {
        ProductValidation.validate(command.price(), command.categoryId(), categoryRepository);

        Product existing = productRepository.findById(command.id())
            .orElseThrow(() -> new ProductNotFoundException("Produto não encontrado: " + command.id()));

        return productRepository.save(new Product(
            existing.id(), command.name(), command.description(), command.price(), command.categoryId(),
            existing.active(), existing.createdAt(), OffsetDateTime.now(), command.version()
        ));
    }
}
