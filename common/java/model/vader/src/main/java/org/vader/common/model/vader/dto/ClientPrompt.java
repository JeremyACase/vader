package org.vader.common.model.vader.dto;

import jakarta.persistence.Lob;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

/** DTO representing a client-submitted prompt, with optional multipart file attachments. */
public class ClientPrompt extends AbstractModel {

    @Lob
    @NotNull
    private String text;

    private List<MultipartFile> files = new ArrayList<>();

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

    public List<MultipartFile> getFiles() {
        return this.files;
    }

    public void setFiles(List<MultipartFile> files) {
        this.files = files;
    }
}
