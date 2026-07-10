package br.com.commercehub.catalog.application.usecase;

import br.com.commercehub.catalog.application.port.ProdutoRepository;
import br.com.commercehub.catalog.domain.model.Produto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * GET list de produtos: só os ativos, paginado, ordenado por {@code createdAt DESC} quando
 * o chamador não pede outra ordem.
 *
 * <p>A ordenação padrão mora aqui, e não no {@code @PageableDefault} do controller, porque é
 * regra de negócio do specify.md e vale para qualquer chamador — não só o HTTP. O default de
 * tamanho de página (12), esse sim detalhe de transporte, fica no controller (seção 10 do
 * plan.md).
 */
@Service
@Transactional(readOnly = true)
public class ListarProdutosUseCase {

    private static final Sort ORDENACAO_PADRAO = Sort.by(Sort.Direction.DESC, "createdAt");

    private final ProdutoRepository produtoRepository;

    public ListarProdutosUseCase(ProdutoRepository produtoRepository) {
        this.produtoRepository = produtoRepository;
    }

    public Page<Produto> executar(Pageable pageable) {
        return produtoRepository.findAllAtivos(comOrdenacaoPadrao(pageable));
    }

    private static Pageable comOrdenacaoPadrao(Pageable pageable) {
        return pageable.getSort().isSorted()
            ? pageable
            : PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), ORDENACAO_PADRAO);
    }
}
