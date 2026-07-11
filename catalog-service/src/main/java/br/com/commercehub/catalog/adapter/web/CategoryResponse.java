package br.com.commercehub.catalog.adapter.web;

import br.com.commercehub.catalog.domain.model.Category;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CategoryResponse(
    UUID id, String name, OffsetDateTime createdAt, OffsetDateTime updatedAt, long version
) {

    public static CategoryResponse from(Category category) {
        return new CategoryResponse(
            category.id(), category.name(), category.createdAt(), category.updatedAt(), category.version()
        );
    }
}
