package br.com.commercehub.catalog.adapter.persistence;

import br.com.commercehub.catalog.domain.model.Produto;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "products")
public class ProdutoEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column
    private String description;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private CategoriaEntity category;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Version
    private long version;

    protected ProdutoEntity() {
    }

    public ProdutoEntity(UUID id, String name, String description, BigDecimal price, CategoriaEntity category,
                          boolean active, OffsetDateTime createdAt, OffsetDateTime updatedAt, long version) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.price = price;
        this.category = category;
        this.active = active;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.version = version;
    }

    public static ProdutoEntity fromDomain(Produto produto) {
        return new ProdutoEntity(
            produto.id(), produto.nome(), produto.descricao(), produto.preco(),
            new CategoriaEntity(produto.categoriaId()), produto.ativo(),
            produto.createdAt(), produto.updatedAt(), produto.version()
        );
    }

    public Produto toDomain() {
        return new Produto(id, name, description, price, category.getId(), active, createdAt, updatedAt, version);
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public CategoriaEntity getCategory() {
        return category;
    }

    public boolean isActive() {
        return active;
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
