package br.com.commercehub.catalog.adapter.persistence;

import br.com.commercehub.catalog.application.port.CategoriaRepository;
import br.com.commercehub.catalog.domain.model.Categoria;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class CategoriaRepositoryAdapter implements CategoriaRepository {

    private final CategoriaJpaRepository jpaRepository;
    private final ProdutoJpaRepository produtoJpaRepository;

    public CategoriaRepositoryAdapter(CategoriaJpaRepository jpaRepository, ProdutoJpaRepository produtoJpaRepository) {
        this.jpaRepository = jpaRepository;
        this.produtoJpaRepository = produtoJpaRepository;
    }

    @Override
    public Optional<Categoria> findById(UUID id) {
        return jpaRepository.findById(id).map(CategoriaEntity::toDomain);
    }

    @Override
    public Page<Categoria> findAll(Pageable pageable) {
        return jpaRepository.findAll(pageable).map(CategoriaEntity::toDomain);
    }

    @Override
    public Categoria save(Categoria categoria) {
        return jpaRepository.save(CategoriaEntity.fromDomain(categoria)).toDomain();
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
    public long countProdutosAtivosPorCategoria(UUID categoriaId) {
        return produtoJpaRepository.countByCategory_IdAndActiveTrue(categoriaId);
    }
}
