package org.vader.common.model.vader.dto;

/** DTO representing storage metadata for an uploaded object. */
public class ObjectMetadata extends AbstractModel {

    private String bucketName;

    private String originalFilename;

    private String contentType;

    private Long size;

    @Override
    public String getModelType() {
        return "ObjectMetadata";
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
}
