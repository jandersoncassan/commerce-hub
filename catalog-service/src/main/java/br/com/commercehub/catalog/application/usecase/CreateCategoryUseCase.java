package br.com.commercehub.catalog.application.usecase;

import br.com.commercehub.catalog.application.port.CategoryRepository;
import br.com.commercehub.catalog.domain.exception.CategoryNotFoundException;
import br.com.commercehub.catalog.domain.model.Category;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Criação de categoria. Sem validação de negócio própria — {@code name} obrigatório é Bean
 * Validation no DTO de request (TASK-23), não regra de domínio. Toda a deduplicação por
 * {@code Idempotency-Key} vem de {@link IdempotentCreation}.
 */
@Service
@Transactional
public class CreateCategoryUseCase {

    private final CategoryRepository categoryRepository;
    private final IdempotentCreation idempotentCreation;

    CreateCategoryUseCase(CategoryRepository categoryRepository, IdempotentCreation idempotentCreation) {
        this.categoryRepository = categoryRepository;
        this.idempotentCreation = idempotentCreation;
    }

    /**
     * @param idempotencyKey valor do header {@code Idempotency-Key}, ou {@code null}
     *                       quando o cliente não o enviou (sem deduplicação).
     */
    public CreationResult<Category> execute(CreateCategoryCommand command, UUID idempotencyKey) {
        return idempotentCreation.execute(idempotencyKey, new ResourceCreator<Category>() {

            @Override
            public String resourceType() {
                return "CATEGORY";
            }

            @Override
            public Category create(OffsetDateTime now) {
                return categoryRepository.save(new Category(UUID.randomUUID(), command.name(), now, now, 0L));
            }

            @Override
            public UUID idOf(Category category) {
                return category.id();
            }

            @Override
            public Category findById(UUID id) {
                return categoryRepository.findById(id)
                    .orElseThrow(() -> new CategoryNotFoundException("Categoria não encontrada: " + id));
            }
        });
    }
}
