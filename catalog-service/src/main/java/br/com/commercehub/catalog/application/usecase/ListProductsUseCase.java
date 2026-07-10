package br.com.commercehub.catalog.application.usecase;

import br.com.commercehub.catalog.application.port.ProductRepository;
import br.com.commercehub.catalog.domain.model.Product;
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
public class ListProductsUseCase {

    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "createdAt");

    private final ProductRepository productRepository;

    public ListProductsUseCase(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public Page<Product> execute(Pageable pageable) {
        return productRepository.findAllActive(withDefaultSort(pageable));
    }

    private static Pageable withDefaultSort(Pageable pageable) {
        return pageable.getSort().isSorted()
            ? pageable
            : PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), DEFAULT_SORT);
    }
}
