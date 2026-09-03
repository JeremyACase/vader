package org.vader.common.model.vader.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Lob;

/**
 * Holds the raw bytes of an uploaded file.
 *
 * <p>Kept separate from {@link ObjectMetadataEntity} so that metadata queries never load binary
 * content, and so that the database storage path and the MinIO path share the same metadata
 * structure — this entity is only populated when the database strategy is active.</p>
 */
@Entity
public class FileContentEntity extends AbstractModelEntity {

    @Lob
    private byte[] data;

    @Override
    public String getModelType() {
        return "FileContent";
    }

    public byte[] getData() {
        return this.data;
    }

    public void setData(byte[] data) {
        this.data = data;
    }
}
