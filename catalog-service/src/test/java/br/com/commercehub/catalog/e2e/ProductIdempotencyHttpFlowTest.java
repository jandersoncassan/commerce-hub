package br.com.commercehub.catalog.e2e;

import br.com.commercehub.catalog.adapter.web.ProductCreateRequest;
import br.com.commercehub.catalog.adapter.web.ProductResponse;
import br.com.commercehub.catalog.application.port.CategoryRepository;
import br.com.commercehub.catalog.application.port.ProductRepository;
import br.com.commercehub.catalog.domain.model.Category;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fluxo completo de idempotência (TASK-27), fim-a-fim via HTTP real sobre o app inteiro
 * (Testcontainers Postgres, sem mocks) — diferente dos testes de {@code IdempotentCreation} e
 * do {@code IdempotencyKeyStoreAdapter}, que já cobrem a lógica isolada.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "eureka.client.enabled=false")
@Testcontainers
class ProductIdempotencyHttpFlowTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @Test
    void sequentialPostsWithSameKeyReturn201ThenSameResourceWith200() {
        UUID categoryId = createCategory("Idempotência - sequencial");
        HttpEntity<ProductCreateRequest> request = requestWithKey(categoryId, "Café sequencial", UUID.randomUUID());

        ResponseEntity<ProductResponse> first = restTemplate.postForEntity("/api/catalog/products", request, ProductResponse.class);
        ResponseEntity<ProductResponse> second = restTemplate.postForEntity("/api/catalog/products", request, ProductResponse.class);

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(second.getBody().id()).isEqualTo(first.getBody().id());
    }

    @Test
    void concurrentPostsWithSameKeyCreateExactlyOneResourceInTheDatabase() throws Exception {
        UUID categoryId = createCategory("Idempotência - concorrente");
        HttpEntity<ProductCreateRequest> request = requestWithKey(categoryId, "Café concorrente", UUID.randomUUID());
        int threads = 2;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CyclicBarrier barrier = new CyclicBarrier(threads);

        try {
            List<Future<ResponseEntity<ProductResponse>>> futures = IntStream.range(0, threads)
                .mapToObj(i -> pool.submit(() -> {
                    barrier.await();
                    return restTemplate.postForEntity("/api/catalog/products", request, ProductResponse.class);
                }))
                .toList();

            futures.forEach(this::getResult);

            long createdInCategory = productRepository.findAllActive(PageRequest.of(0, 50)).getContent().stream()
                .filter(product -> product.categoryId().equals(categoryId))
                .count();
            assertThat(createdInCategory).isEqualTo(1);
        } finally {
            pool.shutdown();
        }
    }

    private UUID createCategory(String name) {
        OffsetDateTime now = OffsetDateTime.now();
        return categoryRepository.save(new Category(UUID.randomUUID(), name, now, now, 0L)).id();
    }

    private HttpEntity<ProductCreateRequest> requestWithKey(UUID categoryId, String name, UUID key) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Idempotency-Key", key.toString());
        ProductCreateRequest body = new ProductCreateRequest(name, "Torra média", new BigDecimal("29.90"), categoryId);
        return new HttpEntity<>(body, headers);
    }

    private <T> T getResult(Future<T> future) {
        try {
            return future.get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
