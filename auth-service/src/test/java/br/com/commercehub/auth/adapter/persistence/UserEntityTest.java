package br.com.commercehub.auth.adapter.persistence;

import br.com.commercehub.auth.domain.model.Role;
import br.com.commercehub.auth.domain.model.User;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UserEntityTest {

    @Test
    void roundTripsThroughDomainConversion() {
        User user = new User(
            UUID.randomUUID(), "user@example.com", "hashed-password",
            Set.of(Role.ROLE_CUSTOMER, Role.ROLE_ADMIN), OffsetDateTime.now()
        );

        User roundTripped = UserEntity.fromDomain(user).toDomain();

        assertThat(roundTripped).isEqualTo(user);
    }
}
