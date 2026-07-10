package br.com.commercehub.catalog.domain.exception;

public class DuplicateRequestInProgressException extends RuntimeException {

    public DuplicateRequestInProgressException(String message) {
        super(message);
    }
}
