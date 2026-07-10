package br.com.commercehub.catalog.application.usecase;

import java.math.BigDecimal;
import java.util.UUID;

public record AtualizarProdutoCommand(
    UUID id, String nome, String descricao, BigDecimal preco, UUID categoriaId, long version
) {}
