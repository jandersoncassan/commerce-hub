package br.com.commercehub.catalog.application.usecase;

import br.com.commercehub.catalog.adapter.persistence.CategoryEntity;
import br.com.commercehub.catalog.adapter.persistence.CategoryRepositoryAdapter;
import br.com.commercehub.catalog.domain.model.Category;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE;

/**
 * Teste de integração: mesmo motivo do {@link ListProductsUseCaseTest} — com mock de
 * {@code CategoryRepository}, o critério de ordenação padrão viraria uma tautologia. Só com
 * Postgres real o {@code Sort} aplicado pelo usecase é de fato verificado.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
@Testcontainers
@Import({ListCategoriesUseCase.class, CategoryRepositoryAdapter.class})
class ListCategoriesUseCaseTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ListCategoriesUseCase useCase;

    @BeforeEach
    void populateCatalog() {
        OffsetDateTime now = OffsetDateTime.now();
        save("Antiga", now.minusDays(2));
        save("Nova", now);
        entityManager.clear();
    }

    @Test
    void listWithoutSortReturnsOrderedByCreatedAtDesc() {
        Page<Category> page = useCase.execute(PageRequest.of(0, 10));

        assertThat(page.getContent()).extracting(Category::name).containsExactly("Nova", "Antiga");
        assertThat(page.getTotalElements()).isEqualTo(2);
    }

    @Test
    void explicitCallerSortIsNotOverriddenByTheDefault() {
        Page<Category> page = useCase.execute(
            PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, "createdAt")));

        assertThat(page.getContent()).extracting(Category::name).containsExactly("Antiga", "Nova");
    }

    @Test
    void paginationRespectsThePageSize() {
        Page<Category> page = useCase.execute(PageRequest.of(0, 1));

        assertThat(page.getContent()).extracting(Category::name).containsExactly("Nova");
        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getTotalPages()).isEqualTo(2);
    }

    /**
     * {@code createdAt} explícito e distinto por categoria: duas chamadas a {@code now()}
     * poderiam colidir na precisão de microssegundo do TIMESTAMPTZ e tornar a ordem indefinida.
     */
    private void save(String name, OffsetDateTime createdAt) {
        CategoryEntity category = new CategoryEntity(UUID.randomUUID(), name, createdAt, createdAt, 0L);
        entityManager.persistAndFlush(category);
    }
}
