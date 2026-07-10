package br.com.commercehub.catalog.application.usecase;

import br.com.commercehub.catalog.adapter.persistence.CategoryEntity;
import br.com.commercehub.catalog.adapter.persistence.ProductRepositoryAdapter;
import br.com.commercehub.catalog.domain.model.Product;
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

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE;

/**
 * Teste de integração: com mock de {@code ProductRepository}, o critério (b) viraria
 * {@code verify(repo).findAllActive(...)} — uma tautologia que não exercita nem o filtro de
 * inativos nem a ordenação. Só com Postgres real as duas coisas são de fato verificadas.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
@Testcontainers
@Import({ListProductsUseCase.class, ProductRepositoryAdapter.class})
class ListProductsUseCaseTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ProductRepositoryAdapter productRepository;

    @Autowired
    private ListProductsUseCase useCase;

    private UUID categoryId;

    @BeforeEach
    void populateCatalog() {
        categoryId = createCategory();
        OffsetDateTime now = OffsetDateTime.now();
        save("Antigo", true, now.minusDays(2));
        save("Inativo", false, now.minusDays(1));
        save("Novo", true, now);
        entityManager.clear();
    }

    /** Cobre (b) filtro de inativos e (c) ordem padrão de uma vez — `containsExactly` é estrito na ordem. */
    @Test
    void listWithoutSortReturnsOnlyActiveOrderedByCreatedAtDesc() {
        Page<Product> page = useCase.execute(PageRequest.of(0, 10));

        assertThat(page.getContent()).extracting(Product::name).containsExactly("Novo", "Antigo");
        assertThat(page.getTotalElements()).isEqualTo(2);
    }

    @Test
    void explicitCallerSortIsNotOverriddenByTheDefault() {
        Page<Product> page = useCase.execute(
            PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, "createdAt")));

        assertThat(page.getContent()).extracting(Product::name).containsExactly("Antigo", "Novo");
    }

    @Test
    void paginationRespectsThePageSize() {
        Page<Product> page = useCase.execute(PageRequest.of(0, 1));

        assertThat(page.getContent()).extracting(Product::name).containsExactly("Novo");
        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getTotalPages()).isEqualTo(2);
    }

    /**
     * `createdAt` explícito e distinto por produto: três chamadas a `now()` poderiam colidir na
     * precisão de microssegundo do TIMESTAMPTZ e tornar a ordem indefinida.
     */
    private void save(String name, boolean active, OffsetDateTime createdAt) {
        productRepository.save(new Product(UUID.randomUUID(), name, null, new BigDecimal("10.00"),
            categoryId, active, createdAt, createdAt, 0L));
    }

    private UUID createCategory() {
        OffsetDateTime now = OffsetDateTime.now();
        CategoryEntity category = new CategoryEntity(UUID.randomUUID(), "Periféricos", now, now, 0L);
        entityManager.persistAndFlush(category);
        return category.getId();
    }
}
