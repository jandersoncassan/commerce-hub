package br.com.commercehub.catalog.adapter.persistence;

import br.com.commercehub.catalog.domain.model.Product;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ProductEntityTest {

    @Test
    void fromDomainToDomainIsRoundTrip() {
        OffsetDateTime now = OffsetDateTime.now();
        Product product = new Product(
            UUID.randomUUID(), "Teclado", "Teclado mecânico", new BigDecimal("199.90"),
            UUID.randomUUID(), true, now, now, 3L
        );

        Product result = ProductEntity.fromDomain(product).toDomain();

        assertThat(result).isEqualTo(product);
    }
}
