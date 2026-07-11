package br.com.commercehub.catalog.application.usecase;

import br.com.commercehub.catalog.application.port.CategoryRepository;
import br.com.commercehub.catalog.domain.model.Category;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * GET list de categorias: paginado, ordenado por {@code createdAt DESC} quando o chamador não
 * pede outra ordem — mesmo padrão de {@link ListProductsUseCase} (seção 10 do plan.md). Sem
 * filtro de {@code active}: Categoria não tem esse campo.
 */
@Service
@Transactional(readOnly = true)
public class ListCategoriesUseCase {

    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "createdAt");

    private final CategoryRepository categoryRepository;

    public ListCategoriesUseCase(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    public Page<Category> execute(Pageable pageable) {
        return categoryRepository.findAll(withDefaultSort(pageable));
    }

    private static Pageable withDefaultSort(Pageable pageable) {
        return pageable.getSort().isSorted()
            ? pageable
            : PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), DEFAULT_SORT);
    }
}
