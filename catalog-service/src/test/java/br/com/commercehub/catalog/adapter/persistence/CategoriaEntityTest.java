package br.com.commercehub.catalog.adapter.persistence;

import br.com.commercehub.catalog.domain.model.Categoria;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CategoriaEntityTest {

    @Test
    void fromDomainToDomainDeveSerRoundTrip() {
        OffsetDateTime agora = OffsetDateTime.now();
        Categoria categoria = new Categoria(UUID.randomUUID(), "Periféricos", agora, agora, 2L);

        Categoria resultado = CategoriaEntity.fromDomain(categoria).toDomain();

        assertThat(resultado).isEqualTo(categoria);
    }
}
