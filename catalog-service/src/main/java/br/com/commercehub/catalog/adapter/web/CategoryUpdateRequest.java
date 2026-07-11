package br.com.commercehub.catalog.adapter.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CategoryUpdateRequest(
    @NotBlank String name,
    @NotNull Long version
) {
}
