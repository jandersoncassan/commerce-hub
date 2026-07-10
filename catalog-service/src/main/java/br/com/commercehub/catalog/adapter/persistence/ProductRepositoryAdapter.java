package br.com.commercehub.catalog.adapter.persistence;

import br.com.commercehub.catalog.application.port.ProductRepository;
import br.com.commercehub.catalog.domain.model.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class ProductRepositoryAdapter implements ProductRepository {

    private final ProductJpaRepository jpaRepository;

    public ProductRepositoryAdapter(ProductJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Optional<Product> findById(UUID id) {
        return jpaRepository.findById(id).map(ProductEntity::toDomain);
    }

    @Override
    public Page<Product> findAllActive(Pageable pageable) {
        return jpaRepository.findByActiveTrue(pageable).map(ProductEntity::toDomain);
    }

    /**
     * {@code saveAndFlush}, não {@code save}: o flush é o que faz o Hibernate emitir o UPDATE
     * ainda dentro da chamada, de modo que (a) um conflito de {@code version} vire
     * {@code ObjectOptimisticLockingFailureException} aqui, e não lá no commit da transação,
     * e (b) o {@code version} incrementado já esteja na entidade que devolvemos ao chamador.
     */
    @Override
    public Product save(Product product) {
        return jpaRepository.saveAndFlush(ProductEntity.fromDomain(product)).toDomain();
    }
}
