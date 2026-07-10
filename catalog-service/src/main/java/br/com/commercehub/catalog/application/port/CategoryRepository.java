package br.com.commercehub.catalog.application.port;

import br.com.commercehub.catalog.domain.model.Category;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

public interface CategoryRepository {

    Optional<Category> findById(UUID id);

    Page<Category> findAll(Pageable pageable);

    Category save(Category category);

    void deleteById(UUID id);

    boolean existsById(UUID id);

    long countActiveProductsByCategory(UUID categoryId);
}
