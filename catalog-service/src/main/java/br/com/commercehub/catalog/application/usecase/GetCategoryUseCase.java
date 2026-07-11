package br.com.commercehub.catalog.application.usecase;

import br.com.commercehub.catalog.application.port.CategoryRepository;
import br.com.commercehub.catalog.domain.exception.CategoryNotFoundException;
import br.com.commercehub.catalog.domain.model.Category;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * GET detail de categoria. Diferente de {@link GetProductUseCase}, não há filtro de
 * {@code active} — Categoria não tem esse campo (sem DELETE soft, só o DELETE físico da
 * {@link DeleteCategoryUseCase}).
 */
@Service
@Transactional(readOnly = true)
public class GetCategoryUseCase {

    private final CategoryRepository categoryRepository;

    public GetCategoryUseCase(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    public Category execute(UUID id) {
        return categoryRepository.findById(id)
            .orElseThrow(() -> new CategoryNotFoundException("Categoria não encontrada: " + id));
    }
}
