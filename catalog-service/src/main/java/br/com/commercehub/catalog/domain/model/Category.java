package br.com.commercehub.catalog.domain.model;

import java.time.OffsetDateTime;
import java.util.UUID;

public record Category(
    UUID id, String name,
    OffsetDateTime createdAt, OffsetDateTime updatedAt, long version
) {}
