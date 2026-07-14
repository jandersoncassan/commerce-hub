package br.com.commercehub.auth.domain.model;

import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;

public record User(
    UUID id, String email, String passwordHash, Set<Role> roles, OffsetDateTime createdAt
) {}
