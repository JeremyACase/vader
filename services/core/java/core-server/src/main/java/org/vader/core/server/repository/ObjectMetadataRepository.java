package org.vader.core.server.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.vader.common.model.vader.entity.ObjectMetadataEntity;

/** Spring Data repository for {@link ObjectMetadataEntity}. */
public interface ObjectMetadataRepository extends JpaRepository<ObjectMetadataEntity, String> {
}
