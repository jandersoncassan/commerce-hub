package br.com.commercehub.catalog.application.usecase;

import br.com.commercehub.catalog.application.port.ProdutoRepository;
import br.com.commercehub.catalog.domain.exception.ProdutoNaoEncontradoException;
import br.com.commercehub.catalog.domain.model.Produto;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BuscarProdutoUseCaseTest {

    @Mock
    private ProdutoRepository produtoRepository;

    @InjectMocks
    private BuscarProdutoUseCase useCase;

    @Test
    void produtoAtivoERetornado() {
        Produto ativo = produto(true);
        when(produtoRepository.findById(ativo.id())).thenReturn(Optional.of(ativo));

        assertThat(useCase.executar(ativo.id())).isEqualTo(ativo);
    }

    /** Produto soft-deleted precisa ser indistinguível de inexistente: 404 nos dois casos. */
    @Test
    void produtoInativoLancaProdutoNaoEncontradoException() {
        Produto inativo = produto(false);
        when(produtoRepository.findById(inativo.id())).thenReturn(Optional.of(inativo));

        assertThatThrownBy(() -> useCase.executar(inativo.id()))
            .isInstanceOf(ProdutoNaoEncontradoException.class);
    }

    @Test
    void produtoInexistenteLancaProdutoNaoEncontradoException() {
        UUID id = UUID.randomUUID();
        when(produtoRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.executar(id))
            .isInstanceOf(ProdutoNaoEncontradoException.class);
    }

    private static Produto produto(boolean ativo) {
        OffsetDateTime agora = OffsetDateTime.now();
        return new Produto(UUID.randomUUID(), "Teclado", "Mecânico", new BigDecimal("199.90"),
            UUID.randomUUID(), ativo, agora, agora, 0L);
    }
}
