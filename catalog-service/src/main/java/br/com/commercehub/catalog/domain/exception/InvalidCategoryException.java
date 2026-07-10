package br.com.commercehub.catalog.domain.exception;

/** Categoria referenciada por um produto não existe. Validação de payload (422), não 404. */
public class InvalidCategoryException extends RuntimeException {

    public InvalidCategoryException(String message) {
        super(message);
    }
}
