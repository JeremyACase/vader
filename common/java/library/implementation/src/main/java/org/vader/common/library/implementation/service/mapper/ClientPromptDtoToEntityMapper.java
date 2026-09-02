package org.vader.common.library.implementation.service.mapper;

import java.util.Objects;
import org.springframework.stereotype.Service;
import org.vader.common.library.implementation.interfaces.mapper.InterfaceDtoToEntityMapper;
import org.vader.common.model.vader.dto.ClientPrompt;
import org.vader.common.model.vader.entity.ClientPromptEntity;

/**
 * Maps a {@link ClientPrompt} DTO to a transient {@link ClientPromptEntity}.
 *
 * <p>Only the prompt text is carried over. The DTO's {@code files} are inbound
 * {@code MultipartFile} uploads; persisting their contents needs an object store that is not yet
 * wired up, so they are dropped here rather than half-persisted.</p>
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
