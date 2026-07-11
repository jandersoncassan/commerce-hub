package br.com.commercehub.catalog.adapter.web;

import br.com.commercehub.catalog.application.usecase.CreateCategoryUseCase;
import br.com.commercehub.catalog.application.usecase.CreationResult;
import br.com.commercehub.catalog.application.usecase.DeleteCategoryUseCase;
import br.com.commercehub.catalog.application.usecase.GetCategoryUseCase;
import br.com.commercehub.catalog.application.usecase.ListCategoriesUseCase;
import br.com.commercehub.catalog.application.usecase.UpdateCategoryUseCase;
import br.com.commercehub.catalog.domain.exception.CategoryHasActiveProductsException;
import br.com.commercehub.catalog.domain.model.Category;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CategoryController.class)
class CategoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

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
    void listReturns200() throws Exception {
        Category category = category(UUID.randomUUID());
        Page<Category> page = new PageImpl<>(List.of(category), PageRequest.of(0, 20), 1);
        when(listCategoriesUseCase.execute(any())).thenReturn(page);

        mockMvc.perform(get("/api/catalog/categories"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].name").value("Bebidas"));
    }

    @Test
    void getDetailReturns200() throws Exception {
        UUID id = UUID.randomUUID();
        when(getCategoryUseCase.execute(id)).thenReturn(category(id));

        mockMvc.perform(get("/api/catalog/categories/{id}", id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(id.toString()));
    }

    @Test
    void createReturns201WithLocationHeader() throws Exception {
        UUID id = UUID.randomUUID();
        when(createCategoryUseCase.execute(any(), eq(null))).thenReturn(CreationResult.created(category(id)));
        CategoryCreateRequest request = new CategoryCreateRequest("Bebidas");

        mockMvc.perform(post("/api/catalog/categories")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(header().string("Location", containsString("/api/catalog/categories/" + id)));
    }

    @Test
    void updateReturns200() throws Exception {
        UUID id = UUID.randomUUID();
        when(updateCategoryUseCase.execute(any())).thenReturn(category(id));
        CategoryUpdateRequest request = new CategoryUpdateRequest("Bebidas", 0L);

        mockMvc.perform(put("/api/catalog/categories/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(id.toString()));
    }

    @Test
    void deleteReturns204() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete("/api/catalog/categories/{id}", id))
            .andExpect(status().isNoContent());
    }

    @Test
    void deleteWithActiveProductLinkedReturns422() throws Exception {
        UUID id = UUID.randomUUID();
        doThrow(new CategoryHasActiveProductsException("Categoria possui produtos ativos vinculados: " + id))
            .when(deleteCategoryUseCase).execute(id);

        mockMvc.perform(delete("/api/catalog/categories/{id}", id))
            .andExpect(status().isUnprocessableEntity());
    }

    private static Category category(UUID id) {
        OffsetDateTime now = OffsetDateTime.now();
        return new Category(id, "Bebidas", now, now, 0L);
    }
}
