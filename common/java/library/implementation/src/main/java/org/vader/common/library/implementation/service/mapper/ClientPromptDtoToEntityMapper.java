package org.vader.common.library.implementation.service.mapper;

import java.util.Objects;
import org.springframework.stereotype.Service;
import org.vader.common.library.implementation.interfaces.mapper.InterfaceDtoToEntityMapper;
import org.vader.common.model.vader.dto.ClientPrompt;
import org.vader.common.model.vader.entity.ClientPromptEntity;

/**
 * Maps a {@link ClientPrompt} DTO to a transient {@link ClientPromptEntity}.
 *
 * <p>Only the prompt text is carried over here. Attached files are handled separately by the
 * active file-storage strategy in {@code WorkflowService} before the entity is persisted.</p>
 */
@Service
public class ClientPromptDtoToEntityMapper
    implements InterfaceDtoToEntityMapper<ClientPrompt, ClientPromptEntity> {

    @Override
    public ClientPromptEntity map(final ClientPrompt from) {
        if (Objects.isNull(from)) {
            return null;
        }
        var to = new ClientPromptEntity();
        to.setText(from.getText());
        return to;
    }
}
