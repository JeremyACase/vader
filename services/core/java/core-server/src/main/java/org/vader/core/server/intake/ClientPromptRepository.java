package org.vader.core.server.intake;

import org.springframework.data.jpa.repository.JpaRepository;
import org.vader.common.model.vader.entity.ClientPromptEntity;

/** Spring Data repository for {@link ClientPromptEntity}. */
public interface ClientPromptRepository extends JpaRepository<ClientPromptEntity, String> {
}
