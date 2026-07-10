package br.com.commercehub.catalog.adapter.persistence;

import br.com.commercehub.catalog.domain.model.Category;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "categories")
public class CategoryEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Version
    private long version;

    protected CategoryEntity() {
    }

    /** Referência somente-id, usada como lado @ManyToOne de ProductEntity sem carregar a categoria inteira. */
    public CategoryEntity(UUID id) {
        this.id = id;
    }

    public CategoryEntity(UUID id, String name, OffsetDateTime createdAt, OffsetDateTime updatedAt, long version) {
        this.id = id;
        this.name = name;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.version = version;
    }

    public static CategoryEntity fromDomain(Category category) {
        return new CategoryEntity(
            category.id(), category.name(), category.createdAt(), category.updatedAt(), category.version()
        );
    }

    public Category toDomain() {
        return new Category(id, name, createdAt, updatedAt, version);
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }
}
