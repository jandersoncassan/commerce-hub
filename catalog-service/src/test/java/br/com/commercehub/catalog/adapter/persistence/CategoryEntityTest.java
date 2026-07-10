package br.com.commercehub.catalog.adapter.persistence;

import br.com.commercehub.catalog.domain.model.Category;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CategoryEntityTest {

    @Test
    void fromDomainToDomainIsRoundTrip() {
        OffsetDateTime now = OffsetDateTime.now();
        Category category = new Category(UUID.randomUUID(), "Periféricos", now, now, 2L);

        Category result = CategoryEntity.fromDomain(category).toDomain();

        assertThat(result).isEqualTo(category);
    }
}
