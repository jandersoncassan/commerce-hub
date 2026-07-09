package br.com.commercehub.catalog.domain.model;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CategoriaTest {

    @Test
    void deveArmazenarTodosOsCampos() {
        UUID id = UUID.randomUUID();
        OffsetDateTime agora = OffsetDateTime.now();

        Categoria categoria = new Categoria(id, "Periféricos", agora, agora, 0L);

        assertThat(categoria.id()).isEqualTo(id);
        assertThat(categoria.nome()).isEqualTo("Periféricos");
        assertThat(categoria.createdAt()).isEqualTo(agora);
        assertThat(categoria.updatedAt()).isEqualTo(agora);
        assertThat(categoria.version()).isZero();
    }
}
