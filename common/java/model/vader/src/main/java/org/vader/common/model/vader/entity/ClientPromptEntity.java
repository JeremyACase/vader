package org.vader.common.model.vader.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Lob;
import jakarta.persistence.OneToMany;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;

/** JPA entity representing a client-submitted prompt, with optional file attachments. */
@Entity
public class ClientPromptEntity extends AbstractModelEntity {

    @Lob
    @NotNull
    private String text;

    @OneToMany(mappedBy = "clientPrompt", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private List<ObjectMetadataEntity> files = new ArrayList<>();

    @Override
    public String getModelType() {
        return "ClientPrompt";
    }

    public String getText() {
        return this.text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public List<ObjectMetadataEntity> getFiles() {
        return this.files;
    }

    public void setFiles(List<ObjectMetadataEntity> files) {
        this.files = files;
    }
}
