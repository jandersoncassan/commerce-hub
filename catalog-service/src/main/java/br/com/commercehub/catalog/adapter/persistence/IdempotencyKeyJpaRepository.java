package br.com.commercehub.catalog.adapter.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface IdempotencyKeyJpaRepository extends JpaRepository<IdempotencyKeyEntity, UUID> {
}
