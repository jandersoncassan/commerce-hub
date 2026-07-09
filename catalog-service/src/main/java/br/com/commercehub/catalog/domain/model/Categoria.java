package br.com.commercehub.catalog.domain.model;

import java.time.OffsetDateTime;
import java.util.UUID;

public record Categoria(
    UUID id, String nome,
    OffsetDateTime createdAt, OffsetDateTime updatedAt, long version
) {}
