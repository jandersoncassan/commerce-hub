package br.com.commercehub.catalog.application.usecase;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateProductCommand(
    String name, String description, BigDecimal price, UUID categoryId
) {}
