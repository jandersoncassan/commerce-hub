package br.com.commercehub.catalog.domain.exception;

public class CategoriaInexistenteException extends RuntimeException {

    public CategoriaInexistenteException(String message) {
        super(message);
    }
}
