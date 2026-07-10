package br.com.commercehub.catalog.application.usecase;

import br.com.commercehub.catalog.application.port.ProdutoRepository;
import br.com.commercehub.catalog.domain.exception.ProdutoNaoEncontradoException;
import br.com.commercehub.catalog.domain.model.Produto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * GET detail de produto. Produto inativo (soft-deleted) e produto inexistente são
 * indistinguíveis para o cliente: ambos resultam em 404. É o que dá efeito visível ao
 * DELETE soft do {@link DesativarProdutoUseCase} — a linha continua no banco, mas some
 * da API.
 */
@Service
@Transactional(readOnly = true)
public class BuscarProdutoUseCase {

    private final ProdutoRepository produtoRepository;

    public BuscarProdutoUseCase(ProdutoRepository produtoRepository) {
        this.produtoRepository = produtoRepository;
    }

    public Produto executar(UUID id) {
        return produtoRepository.findById(id)
            .filter(Produto::ativo)
            .orElseThrow(() -> new ProdutoNaoEncontradoException("Produto não encontrado: " + id));
    }
}
