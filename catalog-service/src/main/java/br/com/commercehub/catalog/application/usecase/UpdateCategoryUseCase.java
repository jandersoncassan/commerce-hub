package br.com.commercehub.catalog.application.usecase;

import br.com.commercehub.catalog.application.port.CategoryRepository;
import br.com.commercehub.catalog.domain.exception.CategoryNotFoundException;
import br.com.commercehub.catalog.domain.model.Category;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

/**
 * Atualização total (PUT) de categoria, mesmo padrão de optimistic locking do
 * {@link UpdateProductUseCase}: a categoria montada aqui é uma cópia <em>detached</em> com o
 * {@code version} do request, para que o Hibernate compare esse valor contra a coluna no
 * UPDATE em vez de usar o {@code version} que uma entidade managed teria carregado do banco.
 */
@Service
@Transactional
public class UpdateCategoryUseCase {

    private final CategoryRepository categoryRepository;

    public UpdateCategoryUseCase(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    public Category execute(UpdateCategoryCommand command) {
        Category existing = categoryRepository.findById(command.id())
            .orElseThrow(() -> new CategoryNotFoundException("Categoria não encontrada: " + command.id()));

        return categoryRepository.save(new Category(
            existing.id(), command.name(), existing.createdAt(), OffsetDateTime.now(), command.version()
        ));
    }
}
