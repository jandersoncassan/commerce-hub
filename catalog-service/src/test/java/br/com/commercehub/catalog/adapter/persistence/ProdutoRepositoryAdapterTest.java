package br.com.commercehub.catalog.adapter.persistence;

import br.com.commercehub.catalog.domain.model.Produto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE;

@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
@Testcontainers
@Import(ProdutoRepositoryAdapter.class)
class ProdutoRepositoryAdapterTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ProdutoRepositoryAdapter adapter;

    @Test
    void deveSalvarERecuperarProdutoPeloId() {
        UUID categoriaId = criarCategoria("Periféricos");
        OffsetDateTime agora = OffsetDateTime.now();
        Produto produto = new Produto(
            UUID.randomUUID(), "Teclado", "Mecânico", new BigDecimal("199.90"),
            categoriaId, true, agora, agora, 0L
        );

        Produto salvo = adapter.save(produto);
        entityManager.flush();
        entityManager.clear();

        Optional<Produto> encontrado = adapter.findById(salvo.id());

        assertThat(encontrado).isPresent();
        assertThat(encontrado.get().id()).isEqualTo(salvo.id());
        assertThat(encontrado.get().nome()).isEqualTo("Teclado");
        assertThat(encontrado.get().categoriaId()).isEqualTo(categoriaId);
    }

    @Test
    void findAllAtivosDeveRetornarSoProdutosAtivos() {
        UUID categoriaId = criarCategoria("Periféricos");
        OffsetDateTime agora = OffsetDateTime.now();
        adapter.save(new Produto(UUID.randomUUID(), "Ativo", null, BigDecimal.TEN, categoriaId, true, agora, agora, 0L));
        adapter.save(new Produto(UUID.randomUUID(), "Inativo", null, BigDecimal.TEN, categoriaId, false, agora, agora, 0L));
        entityManager.flush();
        entityManager.clear();

        Page<Produto> pagina = adapter.findAllAtivos(PageRequest.of(0, 10));

        assertThat(pagina.getContent()).hasSize(1);
        assertThat(pagina.getContent().get(0).nome()).isEqualTo("Ativo");
    }

    private UUID criarCategoria(String nome) {
        OffsetDateTime agora = OffsetDateTime.now();
        CategoriaEntity categoria = new CategoriaEntity(UUID.randomUUID(), nome, agora, agora, 0L);
        entityManager.persistAndFlush(categoria);
        return categoria.getId();
    }
}
