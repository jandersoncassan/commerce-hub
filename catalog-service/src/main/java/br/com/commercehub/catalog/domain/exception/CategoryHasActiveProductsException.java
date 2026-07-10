package br.com.commercehub.catalog.domain.exception;

public class CategoryHasActiveProductsException extends RuntimeException {

    public CategoryHasActiveProductsException(String message) {
        super(message);
    }
}
