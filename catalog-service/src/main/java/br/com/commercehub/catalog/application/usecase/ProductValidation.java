package br.com.commercehub.catalog.application.usecase;

import br.com.commercehub.catalog.application.port.CategoryRepository;
import br.com.commercehub.catalog.domain.exception.InvalidCategoryException;
import br.com.commercehub.catalog.domain.exception.InvalidPriceException;

import java.math.BigDecimal;
import java.util.UUID;

/** Validações que criação e atualização de produto compartilham (seção 7 do plan.md). */
final class ProductValidation {

    private ProductValidation() {
    }

    static void validate(BigDecimal price, UUID categoryId, CategoryRepository categoryRepository) {
        if (price == null || price.compareTo(BigDecimal.ZERO) < 0) {
            throw new InvalidPriceException("Preço não pode ser negativo: " + price);
        }
        if (!categoryRepository.existsById(categoryId)) {
            throw new InvalidCategoryException("Categoria não encontrada: " + categoryId);
        }
    }
}
