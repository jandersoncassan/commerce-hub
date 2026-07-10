package br.com.commercehub.catalog.adapter.persistence;

import br.com.commercehub.catalog.application.port.ProdutoRepository;
import br.com.commercehub.catalog.domain.model.Produto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class ProdutoRepositoryAdapter implements ProdutoRepository {

    private final ProdutoJpaRepository jpaRepository;

    public ProdutoRepositoryAdapter(ProdutoJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Optional<Produto> findById(UUID id) {
        return jpaRepository.findById(id).map(ProdutoEntity::toDomain);
    }

    @Override
    public Page<Produto> findAllAtivos(Pageable pageable) {
        return jpaRepository.findByActiveTrue(pageable).map(ProdutoEntity::toDomain);
    }

    /**
     * {@code saveAndFlush}, não {@code save}: o flush é o que faz o Hibernate emitir o UPDATE
     * ainda dentro da chamada, de modo que (a) um conflito de {@code version} vire
     * {@code ObjectOptimisticLockingFailureException} aqui, e não lá no commit da transação,
     * e (b) o {@code version} incrementado já esteja na entidade que devolvemos ao chamador.
     */
    @Override
    public Produto save(Produto produto) {
        return jpaRepository.saveAndFlush(ProdutoEntity.fromDomain(produto)).toDomain();
    }
}
