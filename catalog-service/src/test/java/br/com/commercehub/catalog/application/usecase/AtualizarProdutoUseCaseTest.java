package br.com.commercehub.catalog.application.usecase;

import br.com.commercehub.catalog.adapter.persistence.CategoriaEntity;
import br.com.commercehub.catalog.adapter.persistence.CategoriaRepositoryAdapter;
import br.com.commercehub.catalog.adapter.persistence.ProdutoRepositoryAdapter;
import br.com.commercehub.catalog.domain.exception.CategoriaInexistenteException;
import br.com.commercehub.catalog.domain.exception.PrecoInvalidoException;
import br.com.commercehub.catalog.domain.exception.ProdutoNaoEncontradoException;
import br.com.commercehub.catalog.domain.model.Produto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE;

/**
 * Teste de integração, não de mocks: o critério da TASK-15 é que o {@code version}
 * desatualizado seja de fato rejeitado pelo Hibernate no UPDATE. Um mock de
 * {@code ProdutoRepository} passaria sem exercitar nada disso.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
@Testcontainers
@Import({AtualizarProdutoUseCase.class, ProdutoRepositoryAdapter.class, CategoriaRepositoryAdapter.class})
class AtualizarProdutoUseCaseTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ProdutoRepositoryAdapter produtoRepository;

    @Autowired
    private AtualizarProdutoUseCase useCase;

    @Test
    void atualizaProdutoEIncrementaVersion() {
        UUID categoriaId = criarCategoria("Periféricos");
        Produto salvo = criarProduto(categoriaId);

        Produto atualizado = useCase.executar(new AtualizarProdutoCommand(
            salvo.id(), "Teclado sem fio", "Bluetooth", new BigDecimal("249.90"), categoriaId, salvo.version()));

        assertThat(atualizado.nome()).isEqualTo("Teclado sem fio");
        assertThat(atualizado.preco()).isEqualByComparingTo("249.90");
        assertThat(atualizado.version()).isEqualTo(salvo.version() + 1);
        assertThat(atualizado.createdAt()).isEqualTo(salvo.createdAt());
    }

    @Test
    void versionDesatualizadoLancaObjectOptimisticLockingFailureException() {
        UUID categoriaId = criarCategoria("Periféricos");
        Produto salvo = criarProduto(categoriaId);
        long versionAntigo = salvo.version();

        useCase.executar(new AtualizarProdutoCommand(
            salvo.id(), "Primeira atualização", null, new BigDecimal("10.00"), categoriaId, versionAntigo));

        AtualizarProdutoCommand comReuseDoVersionAntigo = new AtualizarProdutoCommand(
            salvo.id(), "Segunda atualização", null, new BigDecimal("20.00"), categoriaId, versionAntigo);

        assertThatThrownBy(() -> useCase.executar(comReuseDoVersionAntigo))
            .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    @Test
    void produtoInexistenteLancaProdutoNaoEncontradoException() {
        UUID categoriaId = criarCategoria("Periféricos");
        AtualizarProdutoCommand comando = new AtualizarProdutoCommand(
            UUID.randomUUID(), "Fantasma", null, BigDecimal.TEN, categoriaId, 0L);

        assertThatThrownBy(() -> useCase.executar(comando))
            .isInstanceOf(ProdutoNaoEncontradoException.class);
    }

    @Test
    void precoNegativoLancaPrecoInvalidoException() {
        UUID categoriaId = criarCategoria("Periféricos");
        Produto salvo = criarProduto(categoriaId);
        AtualizarProdutoCommand comando = new AtualizarProdutoCommand(
            salvo.id(), "Teclado", null, new BigDecimal("-0.01"), categoriaId, salvo.version());

        assertThatThrownBy(() -> useCase.executar(comando))
            .isInstanceOf(PrecoInvalidoException.class);
    }

    @Test
    void categoriaInexistenteLancaCategoriaInexistenteException() {
        UUID categoriaId = criarCategoria("Periféricos");
        Produto salvo = criarProduto(categoriaId);
        AtualizarProdutoCommand comando = new AtualizarProdutoCommand(
            salvo.id(), "Teclado", null, BigDecimal.TEN, UUID.randomUUID(), salvo.version());

        assertThatThrownBy(() -> useCase.executar(comando))
            .isInstanceOf(CategoriaInexistenteException.class);
    }

    /**
     * Devolve o produto relido do banco, não o objeto em memória: o Postgres trunca
     * TIMESTAMPTZ para microssegundos, então só o valor relido serve de referência para
     * comparar {@code createdAt} depois da atualização.
     */
    private Produto criarProduto(UUID categoriaId) {
        OffsetDateTime agora = OffsetDateTime.now();
        Produto salvo = produtoRepository.save(new Produto(
            UUID.randomUUID(), "Teclado", "Mecânico", new BigDecimal("199.90"), categoriaId, true, agora, agora, 0L));
        entityManager.clear();
        return produtoRepository.findById(salvo.id()).orElseThrow();
    }

    private UUID criarCategoria(String nome) {
        OffsetDateTime agora = OffsetDateTime.now();
        CategoriaEntity categoria = new CategoriaEntity(UUID.randomUUID(), nome, agora, agora, 0L);
        entityManager.persistAndFlush(categoria);
        return categoria.getId();
    }
}
