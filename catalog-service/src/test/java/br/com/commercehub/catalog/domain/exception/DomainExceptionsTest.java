package br.com.commercehub.catalog.domain.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DomainExceptionsTest {

    @Test
    void productNotFoundExceptionCarriesMessage() {
        var exception = new ProductNotFoundException("produto não encontrado");

        assertThat(exception).isInstanceOf(RuntimeException.class);
        assertThat(exception.getMessage()).isEqualTo("produto não encontrado");
    }

    @Test
    void categoryNotFoundExceptionCarriesMessage() {
        var exception = new CategoryNotFoundException("categoria não encontrada");

        assertThat(exception).isInstanceOf(RuntimeException.class);
        assertThat(exception.getMessage()).isEqualTo("categoria não encontrada");
    }

    @Test
    void invalidPriceExceptionCarriesMessage() {
        var exception = new InvalidPriceException("preço não pode ser negativo");

        assertThat(exception).isInstanceOf(RuntimeException.class);
        assertThat(exception.getMessage()).isEqualTo("preço não pode ser negativo");
    }

    @Test
    void invalidCategoryExceptionCarriesMessage() {
        var exception = new InvalidCategoryException("categoria informada não existe");

        assertThat(exception).isInstanceOf(RuntimeException.class);
        assertThat(exception.getMessage()).isEqualTo("categoria informada não existe");
    }

    @Test
    void categoryHasActiveProductsExceptionCarriesMessage() {
        var exception = new CategoryHasActiveProductsException("categoria possui produtos ativos vinculados");

        assertThat(exception).isInstanceOf(RuntimeException.class);
        assertThat(exception.getMessage()).isEqualTo("categoria possui produtos ativos vinculados");
    }

    @Test
    void duplicateRequestInProgressExceptionCarriesMessage() {
        var exception = new DuplicateRequestInProgressException("requisição já está em processamento");

        assertThat(exception).isInstanceOf(RuntimeException.class);
        assertThat(exception.getMessage()).isEqualTo("requisição já está em processamento");
    }
}
