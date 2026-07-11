package br.com.commercehub.catalog.adapter.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface ProductJpaRepository extends JpaRepository<ProductEntity, UUID> {

    Page<ProductEntity> findByActiveTrue(Pageable pageable);

    long countByCategory_IdAndActiveTrue(UUID categoryId);

    void deleteByCategory_IdAndActiveFalse(UUID categoryId);
}
