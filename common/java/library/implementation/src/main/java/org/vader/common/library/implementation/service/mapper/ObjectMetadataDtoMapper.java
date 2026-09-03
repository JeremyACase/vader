package org.vader.common.library.implementation.service.mapper;

import java.util.Objects;
import org.springframework.stereotype.Service;
import org.vader.common.model.vader.dto.ObjectMetadata;
import org.vader.common.model.vader.entity.ObjectMetadataEntity;

/** Maps {@link ObjectMetadataEntity} to {@link ObjectMetadata} DTOs. */
@Service
public class ObjectMetadataDtoMapper
    extends GenericDtoMapper<ObjectMetadataEntity, ObjectMetadata> {

    @Override
    public ObjectMetadata map(final ObjectMetadataEntity from) {
        ObjectMetadata to = null;
        if (Objects.nonNull(from)) {
            to = new ObjectMetadata();
            super.setAbstractModelFields(from, to);
            to.setBucketName(from.getBucketName());
            to.setOriginalFilename(from.getOriginalFilename());
            to.setContentType(from.getContentType());
            to.setSize(from.getSize());
        }
        return to;
    }
}
