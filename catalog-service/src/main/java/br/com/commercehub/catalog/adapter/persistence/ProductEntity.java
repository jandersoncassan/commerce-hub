package br.com.commercehub.catalog.adapter.persistence;

import br.com.commercehub.catalog.domain.model.Product;
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
public class ProductEntity {

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
    private CategoryEntity category;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Version
    private long version;

    protected ProductEntity() {
    }

    public ProductEntity(UUID id, String name, String description, BigDecimal price, CategoryEntity category,
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

    public static ProductEntity fromDomain(Product product) {
        return new ProductEntity(
            product.id(), product.name(), product.description(), product.price(),
            new CategoryEntity(product.categoryId()), product.active(),
            product.createdAt(), product.updatedAt(), product.version()
        );
    }

    public Product toDomain() {
        return new Product(id, name, description, price, category.getId(), active, createdAt, updatedAt, version);
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

    public CategoryEntity getCategory() {
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
