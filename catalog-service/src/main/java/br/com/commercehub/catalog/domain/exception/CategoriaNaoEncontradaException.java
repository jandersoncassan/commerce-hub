package br.com.commercehub.catalog.domain.exception;

public class CategoriaNaoEncontradaException extends RuntimeException {

    public CategoriaNaoEncontradaException(String message) {
        super(message);
    }
}
