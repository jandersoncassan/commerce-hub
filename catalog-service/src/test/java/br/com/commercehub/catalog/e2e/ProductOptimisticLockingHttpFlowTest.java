package br.com.commercehub.catalog.e2e;

import br.com.commercehub.catalog.adapter.web.CategoryCreateRequest;
import br.com.commercehub.catalog.adapter.web.CategoryResponse;
import br.com.commercehub.catalog.adapter.web.ProductCreateRequest;
import br.com.commercehub.catalog.adapter.web.ProductResponse;
import br.com.commercehub.catalog.adapter.web.ProductUpdateRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fluxo completo de optimistic locking (TASK-28), fim-a-fim via HTTP real sobre o app inteiro
 * (Testcontainers Postgres, sem mocks) — diferente do teste de integração do
 * {@code UpdateProductUseCase} (TASK-15), que já cobre a lógica isolada.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "eureka.client.enabled=false")
@Testcontainers
class ProductOptimisticLockingHttpFlowTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void putWithCorrectVersionSucceedsThenReusingStaleVersionReturns409() {
        UUID categoryId = createCategory("Optimistic locking");
        ProductResponse created = createProduct(categoryId, "Café v1");
        String path = "/api/catalog/products/" + created.id();

        ResponseEntity<ProductResponse> firstUpdate = restTemplate.exchange(
            path, HttpMethod.PUT,
            new HttpEntity<>(new ProductUpdateRequest(
                "Café v2", "Torra escura", new BigDecimal("34.90"), categoryId, created.version()
            )),
            ProductResponse.class
        );
        assertThat(firstUpdate.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(firstUpdate.getBody().version()).isEqualTo(created.version() + 1);

        ResponseEntity<String> staleUpdate = restTemplate.exchange(
            path, HttpMethod.PUT,
            new HttpEntity<>(new ProductUpdateRequest(
                "Café v3", "Torra clara", new BigDecimal("39.90"), categoryId, created.version()
            )),
            String.class
        );
        assertThat(staleUpdate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    private UUID createCategory(String name) {
        ResponseEntity<CategoryResponse> response = restTemplate.postForEntity(
            "/api/catalog/categories", new CategoryCreateRequest(name), CategoryResponse.class
        );
        return response.getBody().id();
    }

    private ProductResponse createProduct(UUID categoryId, String name) {
        ProductCreateRequest body = new ProductCreateRequest(name, "Torra média", new BigDecimal("29.90"), categoryId);
        ResponseEntity<ProductResponse> response = restTemplate.postForEntity("/api/catalog/products", body, ProductResponse.class);
        return response.getBody();
    }
}
