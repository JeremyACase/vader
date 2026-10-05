package org.vader.core.server.storage;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.vader.common.model.vader.entity.ObjectMetadataEntity;

/** Spring Data repository for {@link ObjectMetadataEntity}. */
public interface ObjectMetadataRepository extends JpaRepository<ObjectMetadataEntity, String> {

    /**
     * Every object one task attempt uploaded, oldest first.
     *
     * @param taskAttemptId the uploading attempt's id
     * @return its uploads, possibly empty
     */
    List<ObjectMetadataEntity> findByTaskAttemptIdOrderByCreatedAtAsc(String taskAttemptId);
}
