package br.com.commercehub.catalog.adapter.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record ProductUpdateRequest(
    @NotBlank String name,
    String description,
    @NotNull BigDecimal price,
    @NotNull UUID categoryId,
    @NotNull Long version
) {
}
