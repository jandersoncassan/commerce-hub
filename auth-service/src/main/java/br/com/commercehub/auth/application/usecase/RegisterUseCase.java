package br.com.commercehub.auth.application.usecase;

import br.com.commercehub.auth.application.port.PasswordHasher;
import br.com.commercehub.auth.application.port.UserRepository;
import br.com.commercehub.auth.domain.exception.EmailAlreadyRegisteredException;
import br.com.commercehub.auth.domain.model.Role;
import br.com.commercehub.auth.domain.model.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional
public class RegisterUseCase {

    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;

    RegisterUseCase(UserRepository userRepository, PasswordHasher passwordHasher) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
    }

    public User execute(RegisterCommand command) {
        String email = command.email().trim().toLowerCase();

        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyRegisteredException("email já cadastrado: " + email);
        }

        User user = new User(
            UUID.randomUUID(), email, passwordHasher.hash(command.password()),
            Set.of(Role.ROLE_CUSTOMER), OffsetDateTime.now()
        );

        return userRepository.save(user);
    }
}
