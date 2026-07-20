package br.com.commercehub.auth.application.usecase;

import br.com.commercehub.auth.application.port.GeneratedToken;
import br.com.commercehub.auth.domain.model.User;

public record LoginResult(User user, GeneratedToken token) {}
