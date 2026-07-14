package br.com.commercehub.auth.domain.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DomainExceptionsTest {

    @Test
    void invalidCredentialsExceptionCarriesMessage() {
        var exception = new InvalidCredentialsException("credenciais inválidas");

        assertThat(exception).isInstanceOf(RuntimeException.class);
        assertThat(exception.getMessage()).isEqualTo("credenciais inválidas");
    }

    @Test
    void emailAlreadyRegisteredExceptionCarriesMessage() {
        var exception = new EmailAlreadyRegisteredException("email já cadastrado");

        assertThat(exception).isInstanceOf(RuntimeException.class);
        assertThat(exception.getMessage()).isEqualTo("email já cadastrado");
    }
}
