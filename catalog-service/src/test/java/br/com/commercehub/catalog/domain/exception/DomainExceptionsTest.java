package br.com.commercehub.catalog.domain.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DomainExceptionsTest {

    @Test
    void produtoNaoEncontradoExceptionCarregaMensagem() {
        var exception = new ProdutoNaoEncontradoException("produto não encontrado");

        assertThat(exception).isInstanceOf(RuntimeException.class);
        assertThat(exception.getMessage()).isEqualTo("produto não encontrado");
    }

    @Test
    void categoriaNaoEncontradaExceptionCarregaMensagem() {
        var exception = new CategoriaNaoEncontradaException("categoria não encontrada");

        assertThat(exception).isInstanceOf(RuntimeException.class);
        assertThat(exception.getMessage()).isEqualTo("categoria não encontrada");
    }

    @Test
    void precoInvalidoExceptionCarregaMensagem() {
        var exception = new PrecoInvalidoException("preço não pode ser negativo");

        assertThat(exception).isInstanceOf(RuntimeException.class);
        assertThat(exception.getMessage()).isEqualTo("preço não pode ser negativo");
    }

    @Test
    void categoriaInexistenteExceptionCarregaMensagem() {
        var exception = new CategoriaInexistenteException("categoria informada não existe");

        assertThat(exception).isInstanceOf(RuntimeException.class);
        assertThat(exception.getMessage()).isEqualTo("categoria informada não existe");
    }

    @Test
    void categoriaComProdutosAtivosExceptionCarregaMensagem() {
        var exception = new CategoriaComProdutosAtivosException("categoria possui produtos ativos vinculados");

        assertThat(exception).isInstanceOf(RuntimeException.class);
        assertThat(exception.getMessage()).isEqualTo("categoria possui produtos ativos vinculados");
    }
}
