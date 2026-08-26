package org.vader.common.model.vader.dto;

import jakarta.persistence.Transient;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.OffsetDateTime;
import java.util.Objects;

/** Abstract base DTO for models, carrying identity and audit timestamp fields. */
public abstract class AbstractModel {

    private String id = null;

    private OffsetDateTime createdAt = null;

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
        return "AbstractModel";
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
        AbstractModel other = (AbstractModel) o;

        return
            Objects.equals(this.id, other.id)
                && Objects.equals(this.modelType, other.modelType);
    }
}
