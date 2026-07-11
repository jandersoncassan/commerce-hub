package br.com.commercehub.catalog.adapter.web;

import br.com.commercehub.catalog.application.usecase.CreateCategoryUseCase;
import br.com.commercehub.catalog.application.usecase.CreateProductUseCase;
import br.com.commercehub.catalog.application.usecase.DeactivateProductUseCase;
import br.com.commercehub.catalog.application.usecase.DeleteCategoryUseCase;
import br.com.commercehub.catalog.application.usecase.GetCategoryUseCase;
import br.com.commercehub.catalog.application.usecase.GetProductUseCase;
import br.com.commercehub.catalog.application.usecase.ListCategoriesUseCase;
import br.com.commercehub.catalog.application.usecase.ListProductsUseCase;
import br.com.commercehub.catalog.application.usecase.UpdateCategoryUseCase;
import br.com.commercehub.catalog.application.usecase.UpdateProductUseCase;
import br.com.commercehub.catalog.domain.exception.CategoryHasActiveProductsException;
import br.com.commercehub.catalog.domain.exception.CategoryNotFoundException;
import br.com.commercehub.catalog.domain.exception.DuplicateRequestInProgressException;
import br.com.commercehub.catalog.domain.exception.InvalidCategoryException;
import br.com.commercehub.catalog.domain.exception.InvalidPriceException;
import br.com.commercehub.catalog.domain.exception.ProductNotFoundException;
import br.com.commercehub.catalog.domain.model.Product;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Um teste por cenário da tabela da seção 9 do plan.md — cobre o {@link GlobalExceptionHandler}
 * de ponta a ponta via HTTP real (MockMvc), não a exceção isolada. As duas controllers
 * compartilham o mesmo {@code @RestControllerAdvice}, então o slice cobre as duas.
 */
@WebMvcTest(controllers = {ProductController.class, CategoryController.class})
class GlobalExceptionHandlerTest {

    private static final UUID CATEGORY_ID = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CreateProductUseCase createProductUseCase;

    @MockBean
    private UpdateProductUseCase updateProductUseCase;

    @MockBean
    private DeactivateProductUseCase deactivateProductUseCase;

    @MockBean
    private GetProductUseCase getProductUseCase;

    @MockBean
    private ListProductsUseCase listProductsUseCase;

    @MockBean
    private CreateCategoryUseCase createCategoryUseCase;

    @MockBean
    private UpdateCategoryUseCase updateCategoryUseCase;

    @MockBean
    private DeleteCategoryUseCase deleteCategoryUseCase;

    @MockBean
    private GetCategoryUseCase getCategoryUseCase;

    @MockBean
    private ListCategoriesUseCase listCategoriesUseCase;

    @Test
    void malformedJsonBodyReturns400() throws Exception {
        mockMvc.perform(post("/api/catalog/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ not valid json"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void blankRequiredFieldReturns400() throws Exception {
        mockMvc.perform(post("/api/catalog/categories")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void malformedUuidInPathReturns400() throws Exception {
        mockMvc.perform(get("/api/catalog/products/{id}", "not-a-uuid"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void productNotFoundReturns404() throws Exception {
        UUID id = UUID.randomUUID();
        when(getProductUseCase.execute(id)).thenThrow(new ProductNotFoundException("Produto não encontrado: " + id));

        mockMvc.perform(get("/api/catalog/products/{id}", id))
            .andExpect(status().isNotFound());
    }

    @Test
    void categoryNotFoundReturns404() throws Exception {
        UUID id = UUID.randomUUID();
        when(getCategoryUseCase.execute(id)).thenThrow(new CategoryNotFoundException("Categoria não encontrada: " + id));

        mockMvc.perform(get("/api/catalog/categories/{id}", id))
            .andExpect(status().isNotFound());
    }

    @Test
    void staleVersionReturns409() throws Exception {
        UUID id = UUID.randomUUID();
        when(updateProductUseCase.execute(any()))
            .thenThrow(new ObjectOptimisticLockingFailureException(Product.class, id));
        String body = """
            {"name": "Café", "description": "Torra média", "price": 29.90, "categoryId": "%s", "version": 0}
            """.formatted(CATEGORY_ID);

        mockMvc.perform(put("/api/catalog/products/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isConflict());
    }

    @Test
    void duplicateRequestInProgressReturns409() throws Exception {
        when(createProductUseCase.execute(any(), any()))
            .thenThrow(new DuplicateRequestInProgressException("Idempotency-Key em voo"));
        String body = """
            {"name": "Café", "description": "Torra média", "price": 29.90, "categoryId": "%s"}
            """.formatted(CATEGORY_ID);

        mockMvc.perform(post("/api/catalog/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isConflict());
    }

    @Test
    void negativePriceReturns422() throws Exception {
        when(createProductUseCase.execute(any(), any()))
            .thenThrow(new InvalidPriceException("Preço não pode ser negativo"));
        String body = """
            {"name": "Café", "description": "Torra média", "price": -0.01, "categoryId": "%s"}
            """.formatted(CATEGORY_ID);

        mockMvc.perform(post("/api/catalog/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void unknownCategoryReturns422() throws Exception {
        when(createProductUseCase.execute(any(), any()))
            .thenThrow(new InvalidCategoryException("Categoria não existe: " + CATEGORY_ID));
        String body = """
            {"name": "Café", "description": "Torra média", "price": 29.90, "categoryId": "%s"}
            """.formatted(CATEGORY_ID);

        mockMvc.perform(post("/api/catalog/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void categoryWithActiveProductsReturns422() throws Exception {
        UUID id = UUID.randomUUID();
        doThrow(new CategoryHasActiveProductsException("Categoria possui produtos ativos vinculados: " + id))
            .when(deleteCategoryUseCase).execute(id);

        mockMvc.perform(delete("/api/catalog/categories/{id}", id))
            .andExpect(status().isUnprocessableEntity());
    }
}
