package br.com.commercehub.catalog.application.usecase;

import java.util.UUID;

public record UpdateCategoryCommand(UUID id, String name, long version) {}
