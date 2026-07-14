package br.com.commercehub.auth.domain.model;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UserTest {

    @Test
    void storesAllFields() {
        UUID id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();

        User user = new User(
            id, "user@example.com", "hashed-password", Set.of(Role.ROLE_CUSTOMER), now
        );

        assertThat(user.id()).isEqualTo(id);
        assertThat(user.email()).isEqualTo("user@example.com");
        assertThat(user.passwordHash()).isEqualTo("hashed-password");
        assertThat(user.roles()).containsExactly(Role.ROLE_CUSTOMER);
        assertThat(user.createdAt()).isEqualTo(now);
    }
}
