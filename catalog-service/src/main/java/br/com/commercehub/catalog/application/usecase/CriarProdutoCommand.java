package br.com.commercehub.catalog.application.usecase;

import java.math.BigDecimal;
import java.util.UUID;

public record CriarProdutoCommand(
    String nome, String descricao, BigDecimal preco, UUID categoriaId
) {}
