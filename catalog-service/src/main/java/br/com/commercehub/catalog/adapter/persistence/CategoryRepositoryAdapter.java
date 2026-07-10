package br.com.commercehub.catalog.adapter.persistence;

import br.com.commercehub.catalog.application.port.CategoryRepository;
import br.com.commercehub.catalog.domain.model.Category;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class CategoryRepositoryAdapter implements CategoryRepository {

    private final CategoryJpaRepository jpaRepository;
    private final ProductJpaRepository productJpaRepository;

    public CategoryRepositoryAdapter(CategoryJpaRepository jpaRepository, ProductJpaRepository productJpaRepository) {
        this.jpaRepository = jpaRepository;
        this.productJpaRepository = productJpaRepository;
    }

    @Override
    public Optional<Category> findById(UUID id) {
        return jpaRepository.findById(id).map(CategoryEntity::toDomain);
    }

    @Override
    public Page<Category> findAll(Pageable pageable) {
        return jpaRepository.findAll(pageable).map(CategoryEntity::toDomain);
    }

    @Override
    public Category save(Category category) {
        return jpaRepository.save(CategoryEntity.fromDomain(category)).toDomain();
    }

    @Override
    public void deleteById(UUID id) {
        jpaRepository.deleteById(id);
    }

    @Override
    public boolean existsById(UUID id) {
        return jpaRepository.existsById(id);
    }

    @Override
    public long countActiveProductsByCategory(UUID categoryId) {
        return productJpaRepository.countByCategory_IdAndActiveTrue(categoryId);
    }
}
