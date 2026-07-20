package br.com.commercehub.auth.application.usecase;

import br.com.commercehub.auth.application.port.GeneratedToken;
import br.com.commercehub.auth.application.port.PasswordHasher;
import br.com.commercehub.auth.application.port.TokenGenerator;
import br.com.commercehub.auth.application.port.UserRepository;
import br.com.commercehub.auth.domain.exception.InvalidCredentialsException;
import br.com.commercehub.auth.domain.model.Role;
import br.com.commercehub.auth.domain.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoginUseCaseTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordHasher passwordHasher;

    @Mock
    private TokenGenerator tokenGenerator;

    private LoginUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new LoginUseCase(userRepository, passwordHasher, tokenGenerator);
    }

    @Test
    void unknownEmailThrowsInvalidCredentials() {
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(new LoginCommand("user@example.com", "senha12345")))
            .isInstanceOf(InvalidCredentialsException.class);

        verifyNoInteractions(tokenGenerator);
    }

    @Test
    void wrongPasswordThrowsTheExactSameMessageAsUnknownEmail() {
        User user = existingUser();
        when(userRepository.findByEmail(user.email())).thenReturn(Optional.of(user));
        when(passwordHasher.matches("senha-errada", user.passwordHash())).thenReturn(false);
        when(userRepository.findByEmail("outro@example.com")).thenReturn(Optional.empty());

        String messageForWrongPassword = catchMessage(() ->
            useCase.execute(new LoginCommand(user.email(), "senha-errada")));
        String messageForUnknownEmail = catchMessage(() ->
            useCase.execute(new LoginCommand("outro@example.com", "qualquer-senha")));

        assertThat(messageForWrongPassword).isEqualTo(messageForUnknownEmail);
        verifyNoInteractions(tokenGenerator);
    }

    @Test
    void correctCredentialsGenerateTokenAndReturnUserAndToken() {
        User user = existingUser();
        GeneratedToken generatedToken = new GeneratedToken("jwt-token", 3600);
        when(userRepository.findByEmail(user.email())).thenReturn(Optional.of(user));
        when(passwordHasher.matches("senha12345", user.passwordHash())).thenReturn(true);
        when(tokenGenerator.generate(user)).thenReturn(generatedToken);

        LoginResult result = useCase.execute(new LoginCommand(user.email(), "senha12345"));

        assertThat(result.user()).isEqualTo(user);
        assertThat(result.token()).isEqualTo(generatedToken);
        verify(tokenGenerator).generate(user);
    }

    private String catchMessage(Runnable action) {
        try {
            action.run();
            throw new AssertionError("esperava InvalidCredentialsException");
        } catch (InvalidCredentialsException e) {
            return e.getMessage();
        }
    }

    private User existingUser() {
        return new User(
            UUID.randomUUID(), "user@example.com", "hashed-password",
            Set.of(Role.ROLE_CUSTOMER), OffsetDateTime.now()
        );
    }
}
