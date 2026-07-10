package br.com.commercehub.catalog.adapter.persistence;

import br.com.commercehub.catalog.domain.model.Product;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE;

@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
@Testcontainers
@Import(ProductRepositoryAdapter.class)
class ProductRepositoryAdapterTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ProductRepositoryAdapter adapter;

    @Test
    void savesAndRetrievesProductById() {
        UUID categoryId = createCategory("Periféricos");
        OffsetDateTime now = OffsetDateTime.now();
        Product product = new Product(
            UUID.randomUUID(), "Teclado", "Mecânico", new BigDecimal("199.90"),
            categoryId, true, now, now, 0L
        );

        Product saved = adapter.save(product);
        entityManager.flush();
        entityManager.clear();

        Optional<Product> found = adapter.findById(saved.id());

        assertThat(found).isPresent();
        assertThat(found.get().id()).isEqualTo(saved.id());
        assertThat(found.get().name()).isEqualTo("Teclado");
        assertThat(found.get().categoryId()).isEqualTo(categoryId);
    }

    @Test
    void findAllActiveReturnsOnlyActiveProducts() {
        UUID categoryId = createCategory("Periféricos");
        OffsetDateTime now = OffsetDateTime.now();
        adapter.save(new Product(UUID.randomUUID(), "Ativo", null, BigDecimal.TEN, categoryId, true, now, now, 0L));
        adapter.save(new Product(UUID.randomUUID(), "Inativo", null, BigDecimal.TEN, categoryId, false, now, now, 0L));
        entityManager.flush();
        entityManager.clear();

        Page<Product> page = adapter.findAllActive(PageRequest.of(0, 10));

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).name()).isEqualTo("Ativo");
    }

    private UUID createCategory(String name) {
        OffsetDateTime now = OffsetDateTime.now();
        CategoryEntity category = new CategoryEntity(UUID.randomUUID(), name, now, now, 0L);
        entityManager.persistAndFlush(category);
        return category.getId();
    }
}
