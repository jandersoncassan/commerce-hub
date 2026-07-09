package br.com.commercehub.catalog.domain.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record Produto(
    UUID id, String nome, String descricao, BigDecimal preco,
    UUID categoriaId, boolean ativo,
    OffsetDateTime createdAt, OffsetDateTime updatedAt, long version
) {}
