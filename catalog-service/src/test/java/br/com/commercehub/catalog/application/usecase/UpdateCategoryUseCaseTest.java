package br.com.commercehub.catalog.application.usecase;

import br.com.commercehub.catalog.adapter.persistence.CategoryEntity;
import br.com.commercehub.catalog.adapter.persistence.CategoryRepositoryAdapter;
import br.com.commercehub.catalog.domain.exception.CategoryNotFoundException;
import br.com.commercehub.catalog.domain.model.Category;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE;

/**
 * Teste de integração, não de mocks: mesmo motivo do {@link UpdateProductUseCaseTest} — o
 * critério da TASK-19 é o {@code version} desatualizado sendo de fato rejeitado pelo Hibernate
 * no UPDATE.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
@Testcontainers
@Import({UpdateCategoryUseCase.class, CategoryRepositoryAdapter.class})
class UpdateCategoryUseCaseTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private CategoryRepositoryAdapter categoryRepository;

    @Autowired
    private UpdateCategoryUseCase useCase;

    @Test
    void updatesCategoryAndIncrementsVersion() {
        Category saved = createCategory("Periféricos");

        Category updated = useCase.execute(new UpdateCategoryCommand(saved.id(), "Acessórios", saved.version()));

        assertThat(updated.name()).isEqualTo("Acessórios");
        assertThat(updated.version()).isEqualTo(saved.version() + 1);
        assertThat(updated.createdAt()).isEqualTo(saved.createdAt());
    }

    @Test
    void staleVersionThrowsObjectOptimisticLockingFailureException() {
        Category saved = createCategory("Periféricos");
        long staleVersion = saved.version();

        useCase.execute(new UpdateCategoryCommand(saved.id(), "Primeira atualização", staleVersion));

        UpdateCategoryCommand reusingStaleVersion =
            new UpdateCategoryCommand(saved.id(), "Segunda atualização", staleVersion);

        assertThatThrownBy(() -> useCase.execute(reusingStaleVersion))
            .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    @Test
    void unknownCategoryThrowsCategoryNotFoundException() {
        UpdateCategoryCommand command = new UpdateCategoryCommand(UUID.randomUUID(), "Fantasma", 0L);

        assertThatThrownBy(() -> useCase.execute(command))
            .isInstanceOf(CategoryNotFoundException.class);
    }

    /**
     * Devolve a categoria relida do banco, não o objeto em memória: o Postgres trunca
     * TIMESTAMPTZ para microssegundos, então só o valor relido serve de referência para
     * comparar {@code createdAt} depois da atualização.
     */
    private Category createCategory(String name) {
        OffsetDateTime now = OffsetDateTime.now();
        Category saved = categoryRepository.save(new Category(UUID.randomUUID(), name, now, now, 0L));
        entityManager.clear();
        return categoryRepository.findById(saved.id()).orElseThrow();
    }
}
