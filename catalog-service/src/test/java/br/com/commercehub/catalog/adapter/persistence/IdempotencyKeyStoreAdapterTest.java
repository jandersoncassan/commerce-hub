package br.com.commercehub.catalog.adapter.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

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
import static org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE;

@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
@Testcontainers
@Import(IdempotencyKeyStoreAdapter.class)
class IdempotencyKeyStoreAdapterTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private IdempotencyKeyStoreAdapter store;

    @Test
    void onlyOneOfTwoConcurrentInsertsWithTheSameKeyWins() throws Exception {
        UUID key = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        int threads = 2;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CyclicBarrier barrier = new CyclicBarrier(threads);

        try {
            List<Future<Boolean>> futures = IntStream.range(0, threads)
                .mapToObj(i -> pool.submit(() -> {
                    barrier.await();
                    return store.tryInsert(key, "POST", "PRODUCT", now, now.plusHours(24));
                }))
                .toList();

            List<Boolean> results = futures.stream()
                .map(this::getResult)
                .toList();

            assertThat(results).containsExactlyInAnyOrder(true, false);
        } finally {
            pool.shutdown();
        }
    }

    @Test
    void onlyOneOfTwoConcurrentClaimsOfTheSameExpiredKeyWins() throws Exception {
        UUID key = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime expired = now.minusHours(1);
        assertThat(store.tryInsert(key, "POST", "PRODUCT", expired.minusHours(24), expired)).isTrue();

        int threads = 2;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CyclicBarrier barrier = new CyclicBarrier(threads);

        try {
            List<Future<Boolean>> futures = IntStream.range(0, threads)
                .mapToObj(i -> pool.submit(() -> {
                    barrier.await();
                    return store.tryClaimExpired(key, now, now.plusHours(24), now);
                }))
                .toList();

            List<Boolean> results = futures.stream()
                .map(this::getResult)
                .toList();

            assertThat(results).containsExactlyInAnyOrder(true, false);
        } finally {
            pool.shutdown();
        }
    }

    @Test
    void claimingAnExpiredKeyClearsResourceIdAndResponseStatus() {
        UUID key = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime expired = now.minusHours(1);
        store.tryInsert(key, "POST", "PRODUCT", expired.minusHours(24), expired);
        store.markResolved(key, UUID.randomUUID(), 201);

        assertThat(store.tryClaimExpired(key, now, now.plusHours(24), now)).isTrue();

        assertThat(store.findByKey(key)).hasValueSatisfying(record -> {
            assertThat(record.resourceId()).isNull();
            assertThat(record.responseStatus()).isNull();
            assertThat(record.expiresAt()).isAfter(now);
        });
    }

    @Test
    void doesNotClaimAKeyThatHasNotExpiredYet() {
        UUID key = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        store.tryInsert(key, "POST", "PRODUCT", now, now.plusHours(24));

        assertThat(store.tryClaimExpired(key, now, now.plusHours(24), now)).isFalse();
    }

    private Boolean getResult(Future<Boolean> future) {
        try {
            return future.get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
