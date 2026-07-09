package br.com.commercehub.catalog.adapter.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "idempotency_keys")
public class IdempotencyKeyEntity {

    @Id
    @Column(name = "idempotency_key")
    private UUID idempotencyKey;

    @Column(name = "http_method", nullable = false)
    private String httpMethod;

    @Column(name = "resource_type", nullable = false)
    private String resourceType;

    @Column(name = "resource_id")
    private UUID resourceId;

    @Column(name = "response_status")
    private Short responseStatus;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    protected IdempotencyKeyEntity() {
    }

    public IdempotencyKeyEntity(UUID idempotencyKey, String httpMethod, String resourceType, UUID resourceId,
                                 Short responseStatus, OffsetDateTime createdAt, OffsetDateTime expiresAt) {
        this.idempotencyKey = idempotencyKey;
        this.httpMethod = httpMethod;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.responseStatus = responseStatus;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public UUID getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getHttpMethod() {
        return httpMethod;
    }

    public String getResourceType() {
        return resourceType;
    }

    public UUID getResourceId() {
        return resourceId;
    }

    public Short getResponseStatus() {
        return responseStatus;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setResourceId(UUID resourceId) {
        this.resourceId = resourceId;
    }

    public void setResponseStatus(Short responseStatus) {
        this.responseStatus = responseStatus;
    }
}
