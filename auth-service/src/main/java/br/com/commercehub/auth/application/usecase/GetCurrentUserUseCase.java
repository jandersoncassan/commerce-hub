package br.com.commercehub.auth.application.usecase;

import br.com.commercehub.auth.application.port.UserRepository;
import br.com.commercehub.auth.domain.model.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class GetCurrentUserUseCase {

    private final UserRepository userRepository;

    GetCurrentUserUseCase(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Sem exceção de domínio dedicada para o caminho "não encontrado" (seção 9 do plan.md):
     * {@code userId} vem do {@code X-User-Id} propagado pelo gateway a partir de um token que o
     * próprio auth-service assinou para um usuário que existia no login — sem endpoint de
     * delete/desativação nesta fase, esse caminho é inalcançável na prática.
     */
    public User execute(UUID userId) {
        return userRepository.findById(userId).orElseThrow();
    }
}
