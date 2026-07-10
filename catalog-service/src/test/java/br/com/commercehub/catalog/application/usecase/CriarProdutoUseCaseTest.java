package br.com.commercehub.catalog.application.usecase;

import br.com.commercehub.catalog.application.port.CategoriaRepository;
import br.com.commercehub.catalog.application.port.IdempotencyKeyStore;
import br.com.commercehub.catalog.application.port.IdempotencyKeyStore.IdempotencyKeyRecord;
import br.com.commercehub.catalog.application.port.ProdutoRepository;
import br.com.commercehub.catalog.domain.exception.CategoriaInexistenteException;
import br.com.commercehub.catalog.domain.exception.PrecoInvalidoException;
import br.com.commercehub.catalog.domain.exception.RequisicaoDuplicadaEmAndamentoException;
import br.com.commercehub.catalog.domain.model.Produto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CriarProdutoUseCaseTest {

    private static final UUID CATEGORIA_ID = UUID.randomUUID();

    @Mock
    private ProdutoRepository produtoRepository;

    @Mock
    private CategoriaRepository categoriaRepository;

    @Mock
    private IdempotencyKeyStore idempotencyKeyStore;

    @InjectMocks
    private CriarProdutoUseCase useCase;

    @BeforeEach
    void categoriaExistePorPadrao() {
        lenient().when(categoriaRepository.existsById(CATEGORIA_ID)).thenReturn(true);
        lenient().when(produtoRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void precoNegativoLancaPrecoInvalidoException() {
        CriarProdutoCommand comando = comando(new BigDecimal("-0.01"));

        assertThatThrownBy(() -> useCase.executar(comando, null))
            .isInstanceOf(PrecoInvalidoException.class);

        verify(produtoRepository, never()).save(any());
    }

    @Test
    void categoriaInexistenteLancaCategoriaInexistenteException() {
        UUID categoriaDesconhecida = UUID.randomUUID();
        when(categoriaRepository.existsById(categoriaDesconhecida)).thenReturn(false);
        CriarProdutoCommand comando = new CriarProdutoCommand("Café", "Torra média", new BigDecimal("29.90"), categoriaDesconhecida);

        assertThatThrownBy(() -> useCase.executar(comando, null))
            .isInstanceOf(CategoriaInexistenteException.class);

        verify(produtoRepository, never()).save(any());
    }

    @Test
    void semIdempotencyKeySempreCria() {
        ResultadoCriacao<Produto> resultado = useCase.executar(comando(new BigDecimal("29.90")), null);

        assertThat(resultado.criado()).isTrue();
        assertThat(resultado.recurso().nome()).isEqualTo("Café");
        assertThat(resultado.recurso().ativo()).isTrue();
        verify(produtoRepository).save(any());
        verify(idempotencyKeyStore, never()).tryInsert(any(), any(), any(), any(), any());
    }

    @Test
    void chaveNovaGanhaACorridaCriaEResolve() {
        UUID chave = UUID.randomUUID();
        when(idempotencyKeyStore.tryInsert(eq(chave), eq("POST"), eq("PRODUCT"), any(), any())).thenReturn(true);

        ResultadoCriacao<Produto> resultado = useCase.executar(comando(new BigDecimal("29.90")), chave);

        assertThat(resultado.criado()).isTrue();
        verify(produtoRepository).save(any());
        verify(idempotencyKeyStore).markResolved(chave, resultado.recurso().id(), 201);
    }

    @Test
    void chaveJaResolvidaRetornaRecursoExistenteSemCriarDeNovo() {
        UUID chave = UUID.randomUUID();
        UUID produtoId = UUID.randomUUID();
        Produto existente = produto(produtoId);
        when(idempotencyKeyStore.tryInsert(any(), any(), any(), any(), any())).thenReturn(false);
        when(idempotencyKeyStore.findByKey(chave)).thenReturn(Optional.of(registro(produtoId, OffsetDateTime.now().plusHours(1))));
        when(produtoRepository.findById(produtoId)).thenReturn(Optional.of(existente));

        ResultadoCriacao<Produto> resultado = useCase.executar(comando(new BigDecimal("29.90")), chave);

        assertThat(resultado.criado()).isFalse();
        assertThat(resultado.recurso()).isEqualTo(existente);
        verify(produtoRepository, never()).save(any());
    }

    @Test
    void chaveExpiradaReivindicadaCriaNovoRecurso() {
        UUID chave = UUID.randomUUID();
        when(idempotencyKeyStore.tryInsert(any(), any(), any(), any(), any())).thenReturn(false);
        when(idempotencyKeyStore.findByKey(chave))
            .thenReturn(Optional.of(registro(UUID.randomUUID(), OffsetDateTime.now().minusHours(1))));
        when(idempotencyKeyStore.tryClaimExpired(eq(chave), any(), any(), any())).thenReturn(true);

        ResultadoCriacao<Produto> resultado = useCase.executar(comando(new BigDecimal("29.90")), chave);

        assertThat(resultado.criado()).isTrue();
        verify(produtoRepository).save(any());
        verify(idempotencyKeyStore).markResolved(chave, resultado.recurso().id(), 201);
    }

    @Test
    void chaveExpiradaReivindicadaPorOutraRequisicaoLancaRequisicaoDuplicada() {
        UUID chave = UUID.randomUUID();
        when(idempotencyKeyStore.tryInsert(any(), any(), any(), any(), any())).thenReturn(false);
        when(idempotencyKeyStore.findByKey(chave))
            .thenReturn(Optional.of(registro(UUID.randomUUID(), OffsetDateTime.now().minusHours(1))));
        when(idempotencyKeyStore.tryClaimExpired(eq(chave), any(), any(), any())).thenReturn(false);

        assertThatThrownBy(() -> useCase.executar(comando(new BigDecimal("29.90")), chave))
            .isInstanceOf(RequisicaoDuplicadaEmAndamentoException.class);

        verify(produtoRepository, never()).save(any());
    }

    @Test
    void chaveEmVooLancaRequisicaoDuplicadaSemCriar() {
        UUID chave = UUID.randomUUID();
        when(idempotencyKeyStore.tryInsert(any(), any(), any(), any(), any())).thenReturn(false);
        when(idempotencyKeyStore.findByKey(chave))
            .thenReturn(Optional.of(registro(null, OffsetDateTime.now().plusHours(1))));

        assertThatThrownBy(() -> useCase.executar(comando(new BigDecimal("29.90")), chave))
            .isInstanceOf(RequisicaoDuplicadaEmAndamentoException.class);

        verify(produtoRepository, never()).save(any());
        verify(idempotencyKeyStore, never()).markResolved(any(), any(), anyInt());
    }

    @Test
    void chaveDuplicadaQueSumiuAntesDaLeituraLancaRequisicaoDuplicada() {
        UUID chave = UUID.randomUUID();
        when(idempotencyKeyStore.tryInsert(any(), any(), any(), any(), any())).thenReturn(false);
        when(idempotencyKeyStore.findByKey(chave)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.executar(comando(new BigDecimal("29.90")), chave))
            .isInstanceOf(RequisicaoDuplicadaEmAndamentoException.class);

        verify(produtoRepository, never()).save(any());
    }

    private static CriarProdutoCommand comando(BigDecimal preco) {
        return new CriarProdutoCommand("Café", "Torra média", preco, CATEGORIA_ID);
    }

    private static Produto produto(UUID id) {
        OffsetDateTime agora = OffsetDateTime.now();
        return new Produto(id, "Café", "Torra média", new BigDecimal("29.90"), CATEGORIA_ID, true, agora, agora, 0L);
    }

    private static IdempotencyKeyRecord registro(UUID resourceId, OffsetDateTime expiresAt) {
        Integer status = resourceId == null ? null : 201;
        return new IdempotencyKeyRecord(UUID.randomUUID(), "POST", "PRODUCT", resourceId, status,
            expiresAt.minusHours(24), expiresAt);
    }
}
