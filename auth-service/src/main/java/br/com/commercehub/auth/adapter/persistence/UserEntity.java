package br.com.commercehub.auth.adapter.persistence;

import br.com.commercehub.auth.domain.model.Role;
import br.com.commercehub.auth.domain.model.User;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "users")
public class UserEntity {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "role")
    @Enumerated(EnumType.STRING)
    private Set<Role> roles;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected UserEntity() {
    }

    public UserEntity(UUID id, String email, String passwordHash, Set<Role> roles, OffsetDateTime createdAt) {
        this.id = id;
        this.email = email;
        this.passwordHash = passwordHash;
        this.roles = new HashSet<>(roles);
        this.createdAt = createdAt;
    }

    public static UserEntity fromDomain(User user) {
        return new UserEntity(
            user.id(), user.email(), user.passwordHash(), user.roles(), user.createdAt()
        );
    }

    public User toDomain() {
        return new User(id, email, passwordHash, new HashSet<>(roles), createdAt);
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Set<Role> getRoles() {
        return roles;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
