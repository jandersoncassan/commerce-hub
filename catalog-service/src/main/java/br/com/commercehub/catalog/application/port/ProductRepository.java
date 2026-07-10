package br.com.commercehub.catalog.application.port;

import br.com.commercehub.catalog.domain.model.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

public interface ProductRepository {

    Optional<Product> findById(UUID id);

    Page<Product> findAllActive(Pageable pageable);

    Product save(Product product);
}
