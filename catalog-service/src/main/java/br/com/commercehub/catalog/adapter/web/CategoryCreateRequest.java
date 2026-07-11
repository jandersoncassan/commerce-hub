package br.com.commercehub.catalog.adapter.web;

import jakarta.validation.constraints.NotBlank;

public record CategoryCreateRequest(
    @NotBlank String name
) {
}
