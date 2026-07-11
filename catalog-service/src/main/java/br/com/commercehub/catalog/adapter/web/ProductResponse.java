package br.com.commercehub.catalog.adapter.web;

import br.com.commercehub.catalog.domain.model.Product;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ProductResponse(
    UUID id, String name, String description, BigDecimal price, UUID categoryId, boolean active,
    OffsetDateTime createdAt, OffsetDateTime updatedAt, long version
) {

    public static ProductResponse from(Product product) {
        return new ProductResponse(
            product.id(), product.name(), product.description(), product.price(), product.categoryId(),
            product.active(), product.createdAt(), product.updatedAt(), product.version()
        );
    }
}
