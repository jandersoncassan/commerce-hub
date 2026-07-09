package br.com.commercehub.catalog.application.port;

import br.com.commercehub.catalog.domain.model.Produto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

public interface ProdutoRepository {

    Optional<Produto> findById(UUID id);

    Page<Produto> findAllAtivos(Pageable pageable);

    Produto save(Produto produto);
}
