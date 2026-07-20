package br.com.commercehub.auth.adapter.persistence;

import br.com.commercehub.auth.domain.exception.EmailAlreadyRegisteredException;
import br.com.commercehub.auth.domain.model.Role;
import br.com.commercehub.auth.domain.model.User;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE;

@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
@Testcontainers
@Import(UserRepositoryAdapter.class)
class UserRepositoryAdapterTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private UserRepositoryAdapter adapter;

    @Test
    void savesAndFindsUserByNormalizedEmail() {
        User user = newUser("user@example.com");

        adapter.save(user);
        Optional<User> found = adapter.findByEmail("user@example.com");

        assertThat(found).isPresent();
        assertThat(found.get().id()).isEqualTo(user.id());
        assertThat(found.get().roles()).containsExactly(Role.ROLE_CUSTOMER);
        assertThat(adapter.existsByEmail("user@example.com")).isTrue();
    }

    /**
     * Duas transações reais e concorrentes (threads separadas, cada uma com sua própria conexão)
     * tentam inserir o mesmo email ao mesmo tempo — não uma simulação sequencial. O
     * {@link CountDownLatch} garante que ambas cheguem ao {@code save} juntas, forçando o banco
     * (não o código da aplicação) a decidir quem vence via a constraint UNIQUE.
     */
    @Test
    void concurrentSavesWithSameEmailOnlyOneSucceeds() throws Exception {
        String email = "concorrente@example.com";
        int attempts = 2;

        ExecutorService executor = Executors.newFixedThreadPool(attempts);
        CountDownLatch ready = new CountDownLatch(attempts);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Optional<Exception>>> futures = new ArrayList<>();

        for (int i = 0; i < attempts; i++) {
            futures.add(executor.submit(() -> {
                ready.countDown();
                start.await();
                try {
                    adapter.save(newUser(email));
                    return Optional.<Exception>empty();
                } catch (Exception e) {
                    return Optional.of(e);
                }
            }));
        }

        ready.await();
        start.countDown();

        List<Optional<Exception>> results = new ArrayList<>();
        for (Future<Optional<Exception>> future : futures) {
            results.add(future.get());
        }
        executor.shutdown();

        long successes = results.stream().filter(Optional::isEmpty).count();
        long emailConflicts = results.stream()
            .flatMap(Optional::stream)
            .filter(EmailAlreadyRegisteredException.class::isInstance)
            .count();

        assertThat(successes).isEqualTo(1);
        assertThat(emailConflicts).isEqualTo(1);
    }

    private User newUser(String email) {
        return new User(UUID.randomUUID(), email, "hashed-password", Set.of(Role.ROLE_CUSTOMER), OffsetDateTime.now());
    }
}
