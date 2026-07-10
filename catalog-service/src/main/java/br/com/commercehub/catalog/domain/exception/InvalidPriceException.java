package br.com.commercehub.catalog.domain.exception;

public class InvalidPriceException extends RuntimeException {

    public InvalidPriceException(String message) {
        super(message);
    }
}
