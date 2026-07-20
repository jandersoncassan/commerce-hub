package br.com.commercehub.auth.adapter.persistence;

import br.com.commercehub.auth.application.port.UserRepository;
import br.com.commercehub.auth.domain.exception.EmailAlreadyRegisteredException;
import br.com.commercehub.auth.domain.model.User;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class UserRepositoryAdapter implements UserRepository {

    private final UserJpaRepository jpaRepository;

    public UserRepositoryAdapter(UserJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return jpaRepository.findByEmail(email).map(UserEntity::toDomain);
    }

    @Override
    public Optional<User> findById(UUID id) {
        return jpaRepository.findById(id).map(UserEntity::toDomain);
    }

    @Override
    public boolean existsByEmail(String email) {
        return jpaRepository.existsByEmail(email);
    }

    /**
     * {@code saveAndFlush}, não {@code save}: o INSERT precisa ser emitido ainda dentro desta
     * chamada para que uma violação da constraint UNIQUE em email vire
     * {@code DataIntegrityViolationException} aqui — e não seja adiada silenciosamente para o
     * commit da transação, onde o chamador não teria como traduzi-la. Cobre a janela de corrida
     * entre o {@code existsByEmail} da usecase e este INSERT: sob duas requisições concorrentes
     * com o mesmo email, é a constraint do banco quem decide, e este método garante que o
     * chamador sempre vê a exceção de domínio, nunca a exceção genérica do Spring Data.
     */
    @Override
    public User save(User user) {
        try {
            return jpaRepository.saveAndFlush(UserEntity.fromDomain(user)).toDomain();
        } catch (DataIntegrityViolationException e) {
            throw new EmailAlreadyRegisteredException("email já cadastrado: " + user.email());
        }
    }
}
