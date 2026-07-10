package br.com.commercehub.catalog.application.usecase;

import br.com.commercehub.catalog.application.port.CategoriaRepository;
import br.com.commercehub.catalog.domain.exception.CategoriaInexistenteException;
import br.com.commercehub.catalog.domain.exception.PrecoInvalidoException;

import java.math.BigDecimal;
import java.util.UUID;

/** Validações que criação e atualização de produto compartilham (seção 7 do plan.md). */
final class ProdutoValidacao {

    private ProdutoValidacao() {
    }

    static void validar(BigDecimal preco, UUID categoriaId, CategoriaRepository categoriaRepository) {
        if (preco == null || preco.compareTo(BigDecimal.ZERO) < 0) {
            throw new PrecoInvalidoException("Preço não pode ser negativo: " + preco);
        }
        if (!categoriaRepository.existsById(categoriaId)) {
            throw new CategoriaInexistenteException("Categoria não encontrada: " + categoriaId);
        }
    }
}
