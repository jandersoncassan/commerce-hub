package br.com.commercehub.catalog.domain.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record Product(
    UUID id, String name, String description, BigDecimal price,
    UUID categoryId, boolean active,
    OffsetDateTime createdAt, OffsetDateTime updatedAt, long version
) {}
