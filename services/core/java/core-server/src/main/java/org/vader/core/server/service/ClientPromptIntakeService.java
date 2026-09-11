package org.vader.core.server.service;

import java.util.LinkedHashSet;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.vader.common.library.implementation.service.mapper.ClientPromptDtoToEntityMapper;
import org.vader.common.model.vader.IngressResponse;
import org.vader.common.model.vader.dto.ClientPrompt;
import org.vader.common.model.vader.entity.ClientPromptEntity;
import org.vader.core.server.repository.ClientPromptRepository;
import org.vader.core.server.service.io.ClientPromptOutbox;
import org.vader.core.server.service.strategies.storage.interfaces.InterfaceFileStorageStrategy;

/**
 * Accepts a client prompt for asynchronous decomposition: stores any attachments, persists the
 * prompt, and enqueues an outbox message for {@code ClientPromptInbox} to pick up. Returns an
 * {@link IngressResponse} receipt the caller can use to poll for the eventual workflow.
 *
 * <p>Attachments must be stored here, while the request is in flight, because the
 * {@link MultipartFile} handles do not survive past it.</p>
 */
@Service
public class ClientPromptIntakeService {

    private static final Logger logger =
        LoggerFactory.getLogger(ClientPromptIntakeService.class);

    @Autowired
    private ClientPromptDtoToEntityMapper clientPromptDtoToEntityMapper;

    @Autowired
    private InterfaceFileStorageStrategy fileStorageStrategy;

    @Autowired
    private ClientPromptRepository clientPromptRepository;

    @Autowired
    private ClientPromptOutbox clientPromptOutbox;

    /**
     * Persists the prompt and enqueues it for decomposition.
     *
     * @param clientPrompt the submitted prompt and any attached files
     * @return a receipt identifying the persisted prompt
     */
    @Transactional
    public IngressResponse accept(final ClientPrompt clientPrompt) {
        var promptEntity = this.clientPromptDtoToEntityMapper.map(clientPrompt);
        this.storeAttachments(promptEntity, clientPrompt.getFiles());
        var persisted = this.clientPromptRepository.save(promptEntity);
        logger.info("Accepted client prompt {} ({} file(s))",
            persisted.getId(), persisted.getFiles().size());
        return this.clientPromptOutbox.enqueue(persisted);
    }

    private void storeAttachments(
        final ClientPromptEntity prompt,
        final List<MultipartFile> files) {

        if (files.isEmpty()) {
            return;
        }
        var stored = this.fileStorageStrategy.store(files);
        stored.forEach(file -> file.setClientPrompt(prompt));
        prompt.setFiles(new LinkedHashSet<>(stored));
    }
}
