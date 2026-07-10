package br.com.commercehub.catalog.domain.model;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CategoryTest {

    @Test
    void storesAllFields() {
        UUID id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();

        Category category = new Category(id, "Periféricos", now, now, 0L);

        assertThat(category.id()).isEqualTo(id);
        assertThat(category.name()).isEqualTo("Periféricos");
        assertThat(category.createdAt()).isEqualTo(now);
        assertThat(category.updatedAt()).isEqualTo(now);
        assertThat(category.version()).isZero();
    }
}
