package org.vader.common.model.vader.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.Transient;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.Hibernate;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Abstract base JPA entity for models, providing identity and audit fields.
 *
 * <p>{@code id} is assigned eagerly, at field-initialization time, rather than in a
 * {@code @PrePersist} callback. Deferring it to {@code @PrePersist} would leave {@code id} (and
 * therefore {@link #hashCode()}) {@code null} for any entity added to a {@code HashSet}-backed
 * collection before it's persisted -- its hash bucket would be computed from the pre-persist
 * hash, then silently stop matching once {@code id} changed underneath it.</p>
 */
@Entity
@Inheritance(strategy = InheritanceType.JOINED)
public abstract class AbstractModelEntity {

    @Id
    private String id = UUID.randomUUID().toString();

    @CreationTimestamp
    @Column(updatable = false)
    private OffsetDateTime createdAt = null;

    @UpdateTimestamp
    private OffsetDateTime updatedAt = null;

    @Transient
    private String modelType;

    @Pattern(regexp = "[a-f0-9]{8}(?:-[a-f0-9]{4}){4}[a-f0-9]{8}")
    public String getId() {
        return this.id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public OffsetDateTime getCreatedAt() {
        return this.createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return this.updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    @NotNull
    public String getModelType() {
        return "AbstractModelEntity";
    }

    public void setModelType(String modelType) {
        this.modelType = modelType;
    }

    /**
     * Hashes on {@link #getId()} alone, through the getter: a Hibernate proxy's own fields are
     * never populated, and the class is already part of {@link #equals(Object)}.
     */
    @Override
    public int hashCode() {
        return Objects.hashCode(this.getId());
    }

    /**
     * Equal when both are the same entity class and share an id. Compares through
     * {@link Hibernate#getClass(Object)} and the getters so a lazy proxy equals the entity it
     * stands for: a proxy is a generated subclass whose own fields stay empty, and a call on one
     * is forwarded to its target, where {@code this} is no longer the proxy.
     */
    @Override
    public boolean equals(Object o) {
        var sameClass = Objects.nonNull(o) && Hibernate.getClass(this) == Hibernate.getClass(o);
        return this == o
            || sameClass && Objects.equals(this.getId(), ((AbstractModelEntity) o).getId());
    }
}
