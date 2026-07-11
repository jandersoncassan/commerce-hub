package br.com.commercehub.catalog.application.usecase;

import br.com.commercehub.catalog.adapter.persistence.CategoryEntity;
import br.com.commercehub.catalog.adapter.persistence.CategoryRepositoryAdapter;
import br.com.commercehub.catalog.adapter.persistence.ProductEntity;
import br.com.commercehub.catalog.adapter.persistence.ProductRepositoryAdapter;
import br.com.commercehub.catalog.domain.exception.CategoryHasActiveProductsException;
import br.com.commercehub.catalog.domain.exception.CategoryNotFoundException;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE;

/**
 * Teste de integração, não de mocks: com {@code CategoryRepository} mockado, o critério (c)
 * (categoria só com produtos inativos deleta normalmente) passaria mesmo que o filtro
 * {@code ActiveTrue} de {@code countByCategory_IdAndActiveTrue} fosse removido do
 * {@code ProductJpaRepository} — bastaria o mock devolver {@code 0}. Só persistindo um produto
 * inativo de verdade no banco esse filtro é de fato exercitado.
 *
 * <p>Diferente de {@code save}, {@code deleteById} não dá flush — dentro da mesma transação do
 * teste, o DELETE fica pendente até o commit. Por isso cada assert de "sumiu do banco" dá
 * {@code flush()} antes do {@code clear()}: sem isso, o {@code clear()} descarta a remoção ainda
 * não emitida e a releitura volta a achar a linha.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
@Testcontainers
@Import({DeleteCategoryUseCase.class, CategoryRepositoryAdapter.class, ProductRepositoryAdapter.class})
class DeleteCategoryUseCaseTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private CategoryRepositoryAdapter categoryRepository;

    @Autowired
    private ProductRepositoryAdapter productRepository;

    @Autowired
    private DeleteCategoryUseCase useCase;

    @Test
    void categoryWithoutProductsIsDeleted() {
        UUID categoryId = createCategory("Periféricos");

        useCase.execute(categoryId);
        entityManager.flush();
        entityManager.clear();

        assertThat(categoryRepository.findById(categoryId)).isEmpty();
    }

    @Test
    void categoryWithActiveProductIsNotDeleted() {
        UUID categoryId = createCategory("Periféricos");
        createProduct(categoryId, true);
        entityManager.flush();
        entityManager.clear();

        assertThatThrownBy(() -> useCase.execute(categoryId))
            .isInstanceOf(CategoryHasActiveProductsException.class);

        assertThat(categoryRepository.findById(categoryId)).isPresent();
    }

    @Test
    void categoryWithOnlyInactiveProductsIsDeleted() {
        UUID categoryId = createCategory("Periféricos");
        UUID inactiveProductId = createProduct(categoryId, false);
        createProduct(categoryId, false);
        entityManager.flush();
        entityManager.clear();

        useCase.execute(categoryId);
        entityManager.flush();
        entityManager.clear();

        assertThat(categoryRepository.findById(categoryId)).isEmpty();
        assertThat(productRepository.findById(inactiveProductId)).isEmpty();
    }

    @Test
    void unknownCategoryThrowsCategoryNotFoundException() {
        UUID id = UUID.randomUUID();

        assertThatThrownBy(() -> useCase.execute(id))
            .isInstanceOf(CategoryNotFoundException.class);
    }

    private UUID createCategory(String name) {
        OffsetDateTime now = OffsetDateTime.now();
        CategoryEntity category = new CategoryEntity(UUID.randomUUID(), name, now, now, 0L);
        entityManager.persistAndFlush(category);
        return category.getId();
    }

    private UUID createProduct(UUID categoryId, boolean active) {
        OffsetDateTime now = OffsetDateTime.now();
        CategoryEntity categoryRef = entityManager.find(CategoryEntity.class, categoryId);
        ProductEntity product = new ProductEntity(
            UUID.randomUUID(), "Produto", null, BigDecimal.TEN, categoryRef, active, now, now, 0L
        );
        entityManager.persistAndFlush(product);
        return product.getId();
    }
}
