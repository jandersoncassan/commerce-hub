package br.com.commercehub.catalog.application.usecase;

/**
 * Resultado de uma criação idempotente. {@code created} distingue um recurso
 * recém-criado (o controller responde 201) de um recurso devolvido por uma
 * {@code Idempotency-Key} já resolvida (200).
 */
public record CreationResult<T>(T resource, boolean created) {

    public static <T> CreationResult<T> created(T resource) {
        return new CreationResult<>(resource, true);
    }

    public static <T> CreationResult<T> existing(T resource) {
        return new CreationResult<>(resource, false);
    }
}
