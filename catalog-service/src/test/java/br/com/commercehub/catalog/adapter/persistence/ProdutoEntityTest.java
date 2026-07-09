package br.com.commercehub.catalog.adapter.persistence;

import br.com.commercehub.catalog.domain.model.Produto;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ProdutoEntityTest {

    @Test
    void fromDomainToDomainDeveSerRoundTrip() {
        OffsetDateTime agora = OffsetDateTime.now();
        Produto produto = new Produto(
            UUID.randomUUID(), "Teclado", "Teclado mecânico", new BigDecimal("199.90"),
            UUID.randomUUID(), true, agora, agora, 3L
        );

        Produto resultado = ProdutoEntity.fromDomain(produto).toDomain();

        assertThat(resultado).isEqualTo(produto);
    }
}
