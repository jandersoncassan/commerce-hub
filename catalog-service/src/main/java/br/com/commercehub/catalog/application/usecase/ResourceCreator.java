package br.com.commercehub.catalog.application.usecase;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * O que varia entre criar um produto e criar uma categoria. Tudo o mais — a corrida pela
 * chave de idempotência — é igual e vive em {@link IdempotentCreation}.
 */
interface ResourceCreator<T> {

    /** Valor gravado na coluna {@code resource_type}: {@code PRODUCT} ou {@code CATEGORY}. */
    String resourceType();

    T create(OffsetDateTime now);

    UUID idOf(T resource);

    /** Recarrega o recurso já criado por uma requisição anterior com a mesma chave. */
    T findById(UUID id);
}
