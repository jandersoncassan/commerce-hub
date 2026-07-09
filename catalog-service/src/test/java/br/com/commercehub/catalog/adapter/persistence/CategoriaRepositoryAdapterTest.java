package br.com.commercehub.catalog.adapter.persistence;

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
import static org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE;

@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
@Testcontainers
@Import(CategoriaRepositoryAdapter.class)
class CategoriaRepositoryAdapterTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private CategoriaRepositoryAdapter adapter;

    @Test
    void countProdutosAtivosPorCategoriaDeveContarSoAtivos() {
        UUID categoriaId = criarCategoria("Periféricos");
        criarProduto(categoriaId, true);
        criarProduto(categoriaId, false);
        criarProduto(categoriaId, false);
        entityManager.flush();
        entityManager.clear();

        long total = adapter.countProdutosAtivosPorCategoria(categoriaId);

        assertThat(total).isEqualTo(1);
    }

    private UUID criarCategoria(String nome) {
        OffsetDateTime agora = OffsetDateTime.now();
        CategoriaEntity categoria = new CategoriaEntity(UUID.randomUUID(), nome, agora, agora, 0L);
        entityManager.persistAndFlush(categoria);
        return categoria.getId();
    }

    private void criarProduto(UUID categoriaId, boolean ativo) {
        OffsetDateTime agora = OffsetDateTime.now();
        CategoriaEntity categoriaRef = entityManager.find(CategoriaEntity.class, categoriaId);
        ProdutoEntity produto = new ProdutoEntity(
            UUID.randomUUID(), "Produto", null, BigDecimal.TEN, categoriaRef, ativo, agora, agora, 0L
        );
        entityManager.persistAndFlush(produto);
    }
}
