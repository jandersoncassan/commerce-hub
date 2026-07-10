package br.com.commercehub.catalog.adapter.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE;

@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
@Testcontainers
@Import(CategoryRepositoryAdapter.class)
class CategoryRepositoryAdapterTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private CategoryRepositoryAdapter adapter;

    @Test
    void countActiveProductsByCategoryCountsOnlyActiveOnes() {
        UUID categoryId = createCategory("Periféricos");
        createProduct(categoryId, true);
        createProduct(categoryId, false);
        createProduct(categoryId, false);
        entityManager.flush();
        entityManager.clear();

        long total = adapter.countActiveProductsByCategory(categoryId);

        assertThat(total).isEqualTo(1);
    }

    private UUID createCategory(String name) {
        OffsetDateTime now = OffsetDateTime.now();
        CategoryEntity category = new CategoryEntity(UUID.randomUUID(), name, now, now, 0L);
        entityManager.persistAndFlush(category);
        return category.getId();
    }

    private void createProduct(UUID categoryId, boolean active) {
        OffsetDateTime now = OffsetDateTime.now();
        CategoryEntity categoryRef = entityManager.find(CategoryEntity.class, categoryId);
        ProductEntity product = new ProductEntity(
            UUID.randomUUID(), "Produto", null, BigDecimal.TEN, categoryRef, active, now, now, 0L
        );
        entityManager.persistAndFlush(product);
    }
}
