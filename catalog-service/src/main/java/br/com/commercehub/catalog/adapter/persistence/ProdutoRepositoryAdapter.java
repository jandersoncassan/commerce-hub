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

    @Override
    public Produto save(Produto produto) {
        return jpaRepository.save(ProdutoEntity.fromDomain(produto)).toDomain();
    }
}
