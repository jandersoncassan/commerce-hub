package br.com.commercehub.catalog.application.usecase;

/**
 * Resultado de uma criação idempotente. {@code criado} distingue um recurso
 * recém-criado (o controller responde 201) de um recurso devolvido por uma
 * {@code Idempotency-Key} já resolvida (200).
 */
public record ResultadoCriacao<T>(T recurso, boolean criado) {

    public static <T> ResultadoCriacao<T> novo(T recurso) {
        return new ResultadoCriacao<>(recurso, true);
    }

    public static <T> ResultadoCriacao<T> existente(T recurso) {
        return new ResultadoCriacao<>(recurso, false);
    }
}
