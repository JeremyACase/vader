package org.vader.common.library.implementation.service.mapper;

import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.vader.common.model.vader.dto.ClientPrompt;
import org.vader.common.model.vader.entity.ClientPromptEntity;

/**
 * Maps {@link ClientPromptEntity} to {@link ClientPrompt} DTOs.
 *
 * <p>The DTO's {@code files} are {@code MultipartFile}s -- an inbound upload concept that cannot be
 * reconstructed from the persisted {@code ObjectMetadataEntity} rows, so the mapped DTO leaves that
 * list empty. Egress the stored file metadata via {@link ObjectMetadataDtoMapper} instead.</p>
 */
@Service
@Transactional
public class ClientPromptDtoMapper extends GenericDtoMapper<ClientPromptEntity, ClientPrompt> {

    @Override
    public ClientPrompt map(final ClientPromptEntity from) {
        ClientPrompt to = null;
        if (Objects.nonNull(from)) {
            to = new ClientPrompt();
            super.setAbstractModelFields(from, to);
            to.setText(from.getText());
        }
        return to;
    }
}
