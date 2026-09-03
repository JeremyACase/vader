package org.vader.common.model.vader.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;

/** JPA entity representing storage metadata for an uploaded object. */
@Entity
public class ObjectMetadataEntity extends AbstractModelEntity {

    private String bucketName;

    private String originalFilename;

    private String contentType;

    private Long size;

    /**
     * Points to the stored file content when the database storage strategy is active.
     * Null when the MinIO strategy is active (content lives in the object store instead).
     */
    @OneToOne(fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "file_content_id")
    private FileContentEntity fileContent;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "object_metadata_client_prompt_join_id")
    private ClientPromptEntity clientPrompt;

    @Override
    public String getModelType() {
        return "ObjectMetadata";
    }

    public ClientPromptEntity getClientPrompt() {
        return this.clientPrompt;
    }

    public void setClientPrompt(ClientPromptEntity clientPrompt) {
        this.clientPrompt = clientPrompt;
    }

    public String getBucketName() {
        return this.bucketName;
    }

    public void setBucketName(String bucketName) {
        this.bucketName = bucketName;
    }

    public String getOriginalFilename() {
        return this.originalFilename;
    }

    public void setOriginalFilename(String originalFilename) {
        this.originalFilename = originalFilename;
    }

    public String getContentType() {
        return this.contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public Long getSize() {
        return this.size;
    }

    public void setSize(Long size) {
        this.size = size;
    }

    public FileContentEntity getFileContent() {
        return this.fileContent;
    }

    public void setFileContent(FileContentEntity fileContent) {
        this.fileContent = fileContent;
    }
}
