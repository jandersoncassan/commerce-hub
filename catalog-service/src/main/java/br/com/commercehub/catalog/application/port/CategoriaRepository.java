package br.com.commercehub.catalog.application.port;

import br.com.commercehub.catalog.domain.model.Categoria;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

public interface CategoriaRepository {

    Optional<Categoria> findById(UUID id);

    Page<Categoria> findAll(Pageable pageable);

    Categoria save(Categoria categoria);

    void deleteById(UUID id);

    boolean existsById(UUID id);

    long countProdutosAtivosPorCategoria(UUID categoriaId);
}
