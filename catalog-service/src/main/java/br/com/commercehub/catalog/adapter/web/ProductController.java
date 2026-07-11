package br.com.commercehub.catalog.adapter.web;

import br.com.commercehub.catalog.application.usecase.CreateProductCommand;
import br.com.commercehub.catalog.application.usecase.CreateProductUseCase;
import br.com.commercehub.catalog.application.usecase.CreationResult;
import br.com.commercehub.catalog.application.usecase.DeactivateProductUseCase;
import br.com.commercehub.catalog.application.usecase.GetProductUseCase;
import br.com.commercehub.catalog.application.usecase.ListProductsUseCase;
import br.com.commercehub.catalog.application.usecase.UpdateProductCommand;
import br.com.commercehub.catalog.application.usecase.UpdateProductUseCase;
import br.com.commercehub.catalog.domain.model.Product;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/catalog/products")
public class ProductController {

    private final CreateProductUseCase createProductUseCase;
    private final UpdateProductUseCase updateProductUseCase;
    private final DeactivateProductUseCase deactivateProductUseCase;
    private final GetProductUseCase getProductUseCase;
    private final ListProductsUseCase listProductsUseCase;

    public ProductController(CreateProductUseCase createProductUseCase, UpdateProductUseCase updateProductUseCase,
                              DeactivateProductUseCase deactivateProductUseCase, GetProductUseCase getProductUseCase,
                              ListProductsUseCase listProductsUseCase) {
        this.createProductUseCase = createProductUseCase;
        this.updateProductUseCase = updateProductUseCase;
        this.deactivateProductUseCase = deactivateProductUseCase;
        this.getProductUseCase = getProductUseCase;
        this.listProductsUseCase = listProductsUseCase;
    }

    @GetMapping
    public Page<ProductResponse> list(@PageableDefault(size = 12) Pageable pageable) {
        return listProductsUseCase.execute(pageable).map(ProductResponse::from);
    }

    @GetMapping("/{id}")
    public ProductResponse get(@PathVariable("id") UUID id) {
        return ProductResponse.from(getProductUseCase.execute(id));
    }

    @PostMapping
    public ResponseEntity<ProductResponse> create(
            @Valid @RequestBody ProductCreateRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) UUID idempotencyKey) {
        CreationResult<Product> result = createProductUseCase.execute(
            new CreateProductCommand(request.name(), request.description(), request.price(), request.categoryId()),
            idempotencyKey
        );
        ProductResponse body = ProductResponse.from(result.resource());
        if (result.created()) {
            URI location = URI.create("/api/catalog/products/" + result.resource().id());
            return ResponseEntity.status(HttpStatus.CREATED).location(location).body(body);
        }
        return ResponseEntity.ok(body);
    }

    @PutMapping("/{id}")
    public ProductResponse update(@PathVariable("id") UUID id, @Valid @RequestBody ProductUpdateRequest request) {
        Product updated = updateProductUseCase.execute(new UpdateProductCommand(
            id, request.name(), request.description(), request.price(), request.categoryId(), request.version()
        ));
        return ProductResponse.from(updated);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deactivate(@PathVariable("id") UUID id) {
        deactivateProductUseCase.execute(id);
        return ResponseEntity.noContent().build();
    }
}
