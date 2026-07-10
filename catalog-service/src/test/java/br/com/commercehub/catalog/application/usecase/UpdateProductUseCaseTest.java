package br.com.commercehub.catalog.application.usecase;

import br.com.commercehub.catalog.adapter.persistence.CategoryEntity;
import br.com.commercehub.catalog.adapter.persistence.CategoryRepositoryAdapter;
import br.com.commercehub.catalog.adapter.persistence.ProductRepositoryAdapter;
import br.com.commercehub.catalog.domain.exception.InvalidCategoryException;
import br.com.commercehub.catalog.domain.exception.InvalidPriceException;
import br.com.commercehub.catalog.domain.exception.ProductNotFoundException;
import br.com.commercehub.catalog.domain.model.Product;
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

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE;

/**
 * Teste de integração, não de mocks: o critério da TASK-15 é que o {@code version}
 * desatualizado seja de fato rejeitado pelo Hibernate no UPDATE. Um mock de
 * {@code ProductRepository} passaria sem exercitar nada disso.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
@Testcontainers
@Import({UpdateProductUseCase.class, ProductRepositoryAdapter.class, CategoryRepositoryAdapter.class})
class UpdateProductUseCaseTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ProductRepositoryAdapter productRepository;

    @Autowired
    private UpdateProductUseCase useCase;

    @Test
    void updatesProductAndIncrementsVersion() {
        UUID categoryId = createCategory("Periféricos");
        Product saved = createProduct(categoryId);

        Product updated = useCase.execute(new UpdateProductCommand(
            saved.id(), "Teclado sem fio", "Bluetooth", new BigDecimal("249.90"), categoryId, saved.version()));

        assertThat(updated.name()).isEqualTo("Teclado sem fio");
        assertThat(updated.price()).isEqualByComparingTo("249.90");
        assertThat(updated.version()).isEqualTo(saved.version() + 1);
        assertThat(updated.createdAt()).isEqualTo(saved.createdAt());
    }

    @Test
    void staleVersionThrowsObjectOptimisticLockingFailureException() {
        UUID categoryId = createCategory("Periféricos");
        Product saved = createProduct(categoryId);
        long staleVersion = saved.version();

        useCase.execute(new UpdateProductCommand(
            saved.id(), "Primeira atualização", null, new BigDecimal("10.00"), categoryId, staleVersion));

        UpdateProductCommand reusingStaleVersion = new UpdateProductCommand(
            saved.id(), "Segunda atualização", null, new BigDecimal("20.00"), categoryId, staleVersion);

        assertThatThrownBy(() -> useCase.execute(reusingStaleVersion))
            .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    @Test
    void unknownProductThrowsProductNotFoundException() {
        UUID categoryId = createCategory("Periféricos");
        UpdateProductCommand command = new UpdateProductCommand(
            UUID.randomUUID(), "Fantasma", null, BigDecimal.TEN, categoryId, 0L);

        assertThatThrownBy(() -> useCase.execute(command))
            .isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    void negativePriceThrowsInvalidPriceException() {
        UUID categoryId = createCategory("Periféricos");
        Product saved = createProduct(categoryId);
        UpdateProductCommand command = new UpdateProductCommand(
            saved.id(), "Teclado", null, new BigDecimal("-0.01"), categoryId, saved.version());

        assertThatThrownBy(() -> useCase.execute(command))
            .isInstanceOf(InvalidPriceException.class);
    }

    @Test
    void unknownCategoryThrowsInvalidCategoryException() {
        UUID categoryId = createCategory("Periféricos");
        Product saved = createProduct(categoryId);
        UpdateProductCommand command = new UpdateProductCommand(
            saved.id(), "Teclado", null, BigDecimal.TEN, UUID.randomUUID(), saved.version());

        assertThatThrownBy(() -> useCase.execute(command))
            .isInstanceOf(InvalidCategoryException.class);
    }

    /**
     * Devolve o produto relido do banco, não o objeto em memória: o Postgres trunca
     * TIMESTAMPTZ para microssegundos, então só o valor relido serve de referência para
     * comparar {@code createdAt} depois da atualização.
     */
    private Product createProduct(UUID categoryId) {
        OffsetDateTime now = OffsetDateTime.now();
        Product saved = productRepository.save(new Product(
            UUID.randomUUID(), "Teclado", "Mecânico", new BigDecimal("199.90"), categoryId, true, now, now, 0L));
        entityManager.clear();
        return productRepository.findById(saved.id()).orElseThrow();
    }

    private UUID createCategory(String name) {
        OffsetDateTime now = OffsetDateTime.now();
        CategoryEntity category = new CategoryEntity(UUID.randomUUID(), name, now, now, 0L);
        entityManager.persistAndFlush(category);
        return category.getId();
    }
}
