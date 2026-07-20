package br.com.commercehub.auth.application.usecase;

import br.com.commercehub.auth.application.port.GeneratedToken;
import br.com.commercehub.auth.application.port.PasswordHasher;
import br.com.commercehub.auth.application.port.TokenGenerator;
import br.com.commercehub.auth.application.port.UserRepository;
import br.com.commercehub.auth.domain.exception.InvalidCredentialsException;
import br.com.commercehub.auth.domain.model.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class LoginUseCase {

    /**
     * Mesma mensagem para email inexistente e senha incorreta (specify.md): distinguir os dois
     * casos vazaria se um email está cadastrado ou não.
     */
    private static final String INVALID_CREDENTIALS_MESSAGE = "credenciais inválidas";

    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final TokenGenerator tokenGenerator;

    LoginUseCase(UserRepository userRepository, PasswordHasher passwordHasher, TokenGenerator tokenGenerator) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
        this.tokenGenerator = tokenGenerator;
    }

    public LoginResult execute(LoginCommand command) {
        String email = command.email().trim().toLowerCase();

        User user = userRepository.findByEmail(email).orElseThrow(this::invalidCredentials);

        if (!passwordHasher.matches(command.password(), user.passwordHash())) {
            throw invalidCredentials();
        }

        GeneratedToken token = tokenGenerator.generate(user);
        return new LoginResult(user, token);
    }

    private InvalidCredentialsException invalidCredentials() {
        return new InvalidCredentialsException(INVALID_CREDENTIALS_MESSAGE);
    }
}
