package br.com.commercehub.catalog.adapter.web;

import br.com.commercehub.catalog.application.usecase.CreateCategoryCommand;
import br.com.commercehub.catalog.application.usecase.CreateCategoryUseCase;
import br.com.commercehub.catalog.application.usecase.CreationResult;
import br.com.commercehub.catalog.application.usecase.DeleteCategoryUseCase;
import br.com.commercehub.catalog.application.usecase.GetCategoryUseCase;
import br.com.commercehub.catalog.application.usecase.ListCategoriesUseCase;
import br.com.commercehub.catalog.application.usecase.UpdateCategoryCommand;
import br.com.commercehub.catalog.application.usecase.UpdateCategoryUseCase;
import br.com.commercehub.catalog.domain.exception.CategoryHasActiveProductsException;
import br.com.commercehub.catalog.domain.model.Category;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
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
@RequestMapping("/api/catalog/categories")
public class CategoryController {

    private final CreateCategoryUseCase createCategoryUseCase;
    private final UpdateCategoryUseCase updateCategoryUseCase;
    private final DeleteCategoryUseCase deleteCategoryUseCase;
    private final GetCategoryUseCase getCategoryUseCase;
    private final ListCategoriesUseCase listCategoriesUseCase;

    public CategoryController(CreateCategoryUseCase createCategoryUseCase, UpdateCategoryUseCase updateCategoryUseCase,
                               DeleteCategoryUseCase deleteCategoryUseCase, GetCategoryUseCase getCategoryUseCase,
                               ListCategoriesUseCase listCategoriesUseCase) {
        this.createCategoryUseCase = createCategoryUseCase;
        this.updateCategoryUseCase = updateCategoryUseCase;
        this.deleteCategoryUseCase = deleteCategoryUseCase;
        this.getCategoryUseCase = getCategoryUseCase;
        this.listCategoriesUseCase = listCategoriesUseCase;
    }

    @GetMapping
    public Page<CategoryResponse> list(@PageableDefault(size = 20) Pageable pageable) {
        return listCategoriesUseCase.execute(pageable).map(CategoryResponse::from);
    }

    @GetMapping("/{id}")
    public CategoryResponse get(@PathVariable("id") UUID id) {
        return CategoryResponse.from(getCategoryUseCase.execute(id));
    }

    @PostMapping
    public ResponseEntity<CategoryResponse> create(
            @Valid @RequestBody CategoryCreateRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) UUID idempotencyKey) {
        CreationResult<Category> result = createCategoryUseCase.execute(
            new CreateCategoryCommand(request.name()), idempotencyKey
        );
        CategoryResponse body = CategoryResponse.from(result.resource());
        if (result.created()) {
            URI location = URI.create("/api/catalog/categories/" + result.resource().id());
            return ResponseEntity.status(HttpStatus.CREATED).location(location).body(body);
        }
        return ResponseEntity.ok(body);
    }

    @PutMapping("/{id}")
    public CategoryResponse update(@PathVariable("id") UUID id, @Valid @RequestBody CategoryUpdateRequest request) {
        Category updated = updateCategoryUseCase.execute(
            new UpdateCategoryCommand(id, request.name(), request.version())
        );
        return CategoryResponse.from(updated);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") UUID id) {
        deleteCategoryUseCase.execute(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Mapeamento local e provisório: o {@code GlobalExceptionHandler} da TASK-26 cobrirá esta e
     * as demais exceções de domínio para todos os controllers; até lá, o critério de aceite da
     * TASK-25 (DELETE 422 com produto ativo vinculado) precisa desse handler aqui.
     */
    @ExceptionHandler(CategoryHasActiveProductsException.class)
    public ResponseEntity<Void> handleCategoryHasActiveProducts(CategoryHasActiveProductsException ex) {
        return ResponseEntity.unprocessableEntity().build();
    }
}
