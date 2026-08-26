package org.vader.common.model.vader.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Transient;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/** Abstract base JPA entity for models, providing identity and audit fields. */
@Entity
@Inheritance(strategy = InheritanceType.JOINED)
public abstract class AbstractModelEntity {

    @Id
    private String id = null;

    @PrePersist
    private void generateId() {
        if (Objects.isNull(this.id)) {
            this.id = UUID.randomUUID().toString();
        }
    }

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

    @Override
    public int hashCode() {
        return Objects.hash(this.id, this.modelType);
    }

    @Override
    public boolean equals(Object o) {

        if (this == o) {
            return true;
        }

        if (Objects.isNull(o) || getClass() != o.getClass()) {
            return false;
        }
        AbstractModelEntity other = (AbstractModelEntity) o;

        return
            Objects.equals(this.id, other.id)
                && Objects.equals(this.modelType, other.modelType);
    }
}
