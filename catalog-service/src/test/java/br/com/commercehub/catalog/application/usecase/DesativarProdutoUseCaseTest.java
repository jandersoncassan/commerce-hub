package br.com.commercehub.catalog.application.usecase;

import br.com.commercehub.catalog.application.port.ProdutoRepository;
import br.com.commercehub.catalog.domain.exception.ProdutoNaoEncontradoException;
import br.com.commercehub.catalog.domain.model.Produto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DesativarProdutoUseCaseTest {

    @Mock
    private ProdutoRepository produtoRepository;

    @InjectMocks
    private DesativarProdutoUseCase useCase;

    @Captor
    private ArgumentCaptor<Produto> produtoSalvo;

    @Test
    void produtoAtivoEDesativado() {
        Produto ativo = produto(true);
        when(produtoRepository.findById(ativo.id())).thenReturn(Optional.of(ativo));

        useCase.executar(ativo.id());

        verify(produtoRepository).save(produtoSalvo.capture());
        Produto salvo = produtoSalvo.getValue();
        assertThat(salvo.ativo()).isFalse();
        assertThat(salvo.updatedAt()).isAfter(ativo.updatedAt());
        assertThat(salvo.id()).isEqualTo(ativo.id());
        assertThat(salvo.nome()).isEqualTo(ativo.nome());
        assertThat(salvo.preco()).isEqualByComparingTo(ativo.preco());
        assertThat(salvo.createdAt()).isEqualTo(ativo.createdAt());
    }

    /**
     * Nenhum {@code save} significa nenhum UPDATE — é o que prova, e não apenas aproxima,
     * que {@code updatedAt} permanece intacto para um produto já inativo.
     */
    @Test
    void produtoJaInativoNaoLancaErroENaoGravaDeNovo() {
        Produto inativo = produto(false);
        when(produtoRepository.findById(inativo.id())).thenReturn(Optional.of(inativo));

        assertThatCode(() -> useCase.executar(inativo.id())).doesNotThrowAnyException();

        verify(produtoRepository, never()).save(any());
    }

    @Test
    void produtoInexistenteLancaProdutoNaoEncontradoException() {
        UUID id = UUID.randomUUID();
        when(produtoRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.executar(id))
            .isInstanceOf(ProdutoNaoEncontradoException.class);

        verify(produtoRepository, never()).save(any());
    }

    private static Produto produto(boolean ativo) {
        OffsetDateTime criadoEm = OffsetDateTime.now().minusDays(1);
        return new Produto(UUID.randomUUID(), "Teclado", "Mecânico", new BigDecimal("199.90"),
            UUID.randomUUID(), ativo, criadoEm, criadoEm, 3L);
    }
}
