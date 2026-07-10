package br.com.commercehub.catalog.application.usecase;

import br.com.commercehub.catalog.adapter.persistence.CategoriaEntity;
import br.com.commercehub.catalog.adapter.persistence.ProdutoRepositoryAdapter;
import br.com.commercehub.catalog.domain.model.Produto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE;

/**
 * Teste de integração: com mock de {@code ProdutoRepository}, o critério (b) viraria
 * {@code verify(repo).findAllAtivos(...)} — uma tautologia que não exercita nem o filtro de
 * inativos nem a ordenação. Só com Postgres real as duas coisas são de fato verificadas.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
@Testcontainers
@Import({ListarProdutosUseCase.class, ProdutoRepositoryAdapter.class})
class ListarProdutosUseCaseTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ProdutoRepositoryAdapter produtoRepository;

    @Autowired
    private ListarProdutosUseCase useCase;

    private UUID categoriaId;

    @BeforeEach
    void popularCatalogo() {
        categoriaId = criarCategoria();
        OffsetDateTime agora = OffsetDateTime.now();
        salvar("Antigo", true, agora.minusDays(2));
        salvar("Inativo", false, agora.minusDays(1));
        salvar("Novo", true, agora);
        entityManager.clear();
    }

    /** Cobre (b) filtro de inativos e (c) ordem padrão de uma vez — `containsExactly` é estrito na ordem. */
    @Test
    void listagemSemSortRetornaSoAtivosOrdenadosPorCreatedAtDesc() {
        Page<Produto> pagina = useCase.executar(PageRequest.of(0, 10));

        assertThat(pagina.getContent()).extracting(Produto::nome).containsExactly("Novo", "Antigo");
        assertThat(pagina.getTotalElements()).isEqualTo(2);
    }

    @Test
    void sortExplicitoDoChamadorNaoEhSobrescritoPeloPadrao() {
        Page<Produto> pagina = useCase.executar(
            PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, "createdAt")));

        assertThat(pagina.getContent()).extracting(Produto::nome).containsExactly("Antigo", "Novo");
    }

    @Test
    void paginacaoRespeitaOTamanhoDaPagina() {
        Page<Produto> pagina = useCase.executar(PageRequest.of(0, 1));

        assertThat(pagina.getContent()).extracting(Produto::nome).containsExactly("Novo");
        assertThat(pagina.getTotalElements()).isEqualTo(2);
        assertThat(pagina.getTotalPages()).isEqualTo(2);
    }

    /**
     * `createdAt` explícito e distinto por produto: três chamadas a `now()` poderiam colidir na
     * precisão de microssegundo do TIMESTAMPTZ e tornar a ordem indefinida.
     */
    private void salvar(String nome, boolean ativo, OffsetDateTime createdAt) {
        produtoRepository.save(new Produto(UUID.randomUUID(), nome, null, new BigDecimal("10.00"),
            categoriaId, ativo, createdAt, createdAt, 0L));
    }

    private UUID criarCategoria() {
        OffsetDateTime agora = OffsetDateTime.now();
        CategoriaEntity categoria = new CategoriaEntity(UUID.randomUUID(), "Periféricos", agora, agora, 0L);
        entityManager.persistAndFlush(categoria);
        return categoria.getId();
    }
}
