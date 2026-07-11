package br.com.commercehub.catalog.adapter.web;

import br.com.commercehub.catalog.application.usecase.CreateProductUseCase;
import br.com.commercehub.catalog.application.usecase.CreationResult;
import br.com.commercehub.catalog.application.usecase.DeactivateProductUseCase;
import br.com.commercehub.catalog.application.usecase.GetProductUseCase;
import br.com.commercehub.catalog.application.usecase.ListProductsUseCase;
import br.com.commercehub.catalog.application.usecase.UpdateProductUseCase;
import br.com.commercehub.catalog.domain.model.Product;
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

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProductController.class)
class ProductControllerTest {

    private static final UUID CATEGORY_ID = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

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

    @Test
    void listReturns200() throws Exception {
        Product product = product(UUID.randomUUID());
        Page<Product> page = new PageImpl<>(List.of(product), PageRequest.of(0, 12), 1);
        when(listProductsUseCase.execute(any())).thenReturn(page);

        mockMvc.perform(get("/api/catalog/products"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].name").value("Café"));
    }

    @Test
    void getDetailReturns200() throws Exception {
        UUID id = UUID.randomUUID();
        when(getProductUseCase.execute(id)).thenReturn(product(id));

        mockMvc.perform(get("/api/catalog/products/{id}", id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(id.toString()));
    }

    @Test
    void createReturns201WithLocationHeader() throws Exception {
        UUID id = UUID.randomUUID();
        when(createProductUseCase.execute(any(), eq(null))).thenReturn(CreationResult.created(product(id)));
        ProductCreateRequest request = new ProductCreateRequest("Café", "Torra média", new BigDecimal("29.90"), CATEGORY_ID);

        mockMvc.perform(post("/api/catalog/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(header().string("Location", containsString("/api/catalog/products/" + id)));
    }

    @Test
    void updateReturns200() throws Exception {
        UUID id = UUID.randomUUID();
        when(updateProductUseCase.execute(any())).thenReturn(product(id));
        ProductUpdateRequest request = new ProductUpdateRequest("Café", "Torra média", new BigDecimal("29.90"), CATEGORY_ID, 0L);

        mockMvc.perform(put("/api/catalog/products/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(id.toString()));
    }

    @Test
    void deactivateReturns204() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete("/api/catalog/products/{id}", id))
            .andExpect(status().isNoContent());
    }

    private static Product product(UUID id) {
        OffsetDateTime now = OffsetDateTime.now();
        return new Product(id, "Café", "Torra média", new BigDecimal("29.90"), CATEGORY_ID, true, now, now, 0L);
    }
}
