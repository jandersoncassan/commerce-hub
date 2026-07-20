package br.com.commercehub.auth.application.port;

import br.com.commercehub.auth.domain.model.User;

public interface TokenGenerator {

    GeneratedToken generate(User user);
}
