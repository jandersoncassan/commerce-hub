package br.com.commercehub.catalog.adapter.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE;

/**
 * Usa Testcontainers (não H2): a tabela tem TIMESTAMPTZ e o schema é
 * aplicado via Flyway real, então precisa de um Postgres de verdade.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
@Testcontainers
class IdempotencyKeyEntityTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void writesAndReadsARow() {
        UUID key = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        IdempotencyKeyEntity entity = new IdempotencyKeyEntity(
            key, "POST", "PRODUCT", null, null, now, now.plusHours(24)
        );

        entityManager.persistAndFlush(entity);
        entityManager.clear();

        IdempotencyKeyEntity found = entityManager.find(IdempotencyKeyEntity.class, key);

        assertThat(found).isNotNull();
        assertThat(found.getIdempotencyKey()).isEqualTo(key);
        assertThat(found.getHttpMethod()).isEqualTo("POST");
        assertThat(found.getResourceType()).isEqualTo("PRODUCT");
        assertThat(found.getResourceId()).isNull();
        assertThat(found.getResponseStatus()).isNull();
    }
}
