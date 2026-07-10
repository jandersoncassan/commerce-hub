package br.com.commercehub.catalog.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ProductTest {

    @Test
    void storesAllFields() {
        UUID id = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();

        Product product = new Product(
            id, "Teclado", "Teclado mecânico", new BigDecimal("199.90"),
            categoryId, true, now, now, 0L
        );

        assertThat(product.id()).isEqualTo(id);
        assertThat(product.name()).isEqualTo("Teclado");
        assertThat(product.description()).isEqualTo("Teclado mecânico");
        assertThat(product.price()).isEqualTo(new BigDecimal("199.90"));
        assertThat(product.categoryId()).isEqualTo(categoryId);
        assertThat(product.active()).isTrue();
        assertThat(product.createdAt()).isEqualTo(now);
        assertThat(product.updatedAt()).isEqualTo(now);
        assertThat(product.version()).isZero();
    }
}
