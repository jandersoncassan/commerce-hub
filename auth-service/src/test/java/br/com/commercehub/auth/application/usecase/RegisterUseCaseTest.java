package br.com.commercehub.auth.application.usecase;

import br.com.commercehub.auth.application.port.PasswordHasher;
import br.com.commercehub.auth.application.port.UserRepository;
import br.com.commercehub.auth.domain.exception.EmailAlreadyRegisteredException;
import br.com.commercehub.auth.domain.model.Role;
import br.com.commercehub.auth.domain.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegisterUseCaseTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordHasher passwordHasher;

    private RegisterUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new RegisterUseCase(userRepository, passwordHasher);
        lenient().when(passwordHasher.hash(any())).thenReturn("hashed-password");
        lenient().when(userRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void normalizesEmailBeforeCheckingAndSaving() {
        RegisterCommand command = new RegisterCommand("  User@Example.COM  ", "senha12345");

        useCase.execute(command);

        verify(userRepository).existsByEmail("user@example.com");
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().email()).isEqualTo("user@example.com");
    }

    @Test
    void existingEmailThrowsWithoutSaving() {
        when(userRepository.existsByEmail("user@example.com")).thenReturn(true);
        RegisterCommand command = new RegisterCommand("user@example.com", "senha12345");

        assertThatThrownBy(() -> useCase.execute(command))
            .isInstanceOf(EmailAlreadyRegisteredException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void createdUserAlwaysHasCustomerRole() {
        RegisterCommand command = new RegisterCommand("user@example.com", "senha12345");

        User created = useCase.execute(command);

        assertThat(created.roles()).containsExactly(Role.ROLE_CUSTOMER);
    }

    @Test
    void persistsHashedPasswordNeverRawPassword() {
        when(passwordHasher.hash("senha12345")).thenReturn("bcrypt-hash-value");
        RegisterCommand command = new RegisterCommand("user@example.com", "senha12345");

        User created = useCase.execute(command);

        assertThat(created.passwordHash()).isEqualTo("bcrypt-hash-value");
        assertThat(created.passwordHash()).isNotEqualTo("senha12345");
    }
}
