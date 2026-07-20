package br.com.commercehub.auth.application.usecase;

import br.com.commercehub.auth.application.port.UserRepository;
import br.com.commercehub.auth.domain.model.Role;
import br.com.commercehub.auth.domain.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetCurrentUserUseCaseTest {

    @Mock
    private UserRepository userRepository;

    private GetCurrentUserUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GetCurrentUserUseCase(userRepository);
    }

    @Test
    void existingUserIdReturnsUser() {
        User user = new User(
            UUID.randomUUID(), "user@example.com", "hashed-password",
            Set.of(Role.ROLE_CUSTOMER), OffsetDateTime.now()
        );
        when(userRepository.findById(user.id())).thenReturn(Optional.of(user));

        User found = useCase.execute(user.id());

        assertThat(found).isEqualTo(user);
    }

    @Test
    void unknownUserIdThrows() {
        UUID unknownId = UUID.randomUUID();
        when(userRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(unknownId))
            .isInstanceOf(NoSuchElementException.class);
    }
}
