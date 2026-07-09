package br.com.commercehub.catalog.adapter.persistence;

import br.com.commercehub.catalog.domain.model.Categoria;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "categories")
public class CategoriaEntity {

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

    protected CategoriaEntity() {
    }

    /** Referência somente-id, usada como lado @ManyToOne de ProdutoEntity sem carregar a categoria inteira. */
    public CategoriaEntity(UUID id) {
        this.id = id;
    }

    public CategoriaEntity(UUID id, String name, OffsetDateTime createdAt, OffsetDateTime updatedAt, long version) {
        this.id = id;
        this.name = name;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.version = version;
    }

    public static CategoriaEntity fromDomain(Categoria categoria) {
        return new CategoriaEntity(
            categoria.id(), categoria.nome(), categoria.createdAt(), categoria.updatedAt(), categoria.version()
        );
    }

    public Categoria toDomain() {
        return new Categoria(id, name, createdAt, updatedAt, version);
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
