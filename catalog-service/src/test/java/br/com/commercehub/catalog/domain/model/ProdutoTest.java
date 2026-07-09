package br.com.commercehub.catalog.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ProdutoTest {

    @Test
    void deveArmazenarTodosOsCampos() {
        UUID id = UUID.randomUUID();
        UUID categoriaId = UUID.randomUUID();
        OffsetDateTime agora = OffsetDateTime.now();

        Produto produto = new Produto(
            id, "Teclado", "Teclado mecânico", new BigDecimal("199.90"),
            categoriaId, true, agora, agora, 0L
        );

        assertThat(produto.id()).isEqualTo(id);
        assertThat(produto.nome()).isEqualTo("Teclado");
        assertThat(produto.descricao()).isEqualTo("Teclado mecânico");
        assertThat(produto.preco()).isEqualTo(new BigDecimal("199.90"));
        assertThat(produto.categoriaId()).isEqualTo(categoriaId);
        assertThat(produto.ativo()).isTrue();
        assertThat(produto.createdAt()).isEqualTo(agora);
        assertThat(produto.updatedAt()).isEqualTo(agora);
        assertThat(produto.version()).isZero();
    }
}
