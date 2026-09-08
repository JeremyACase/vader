package org.vader.core.server.controller.dao;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.vader.common.library.dao.component.EntityDao;
import org.vader.common.library.dao.controller.GenericVaderDaoController;
import org.vader.common.library.implementation.interfaces.mapper.InterfaceEntityToDtoMapper;
import org.vader.common.library.implementation.service.mapper.ObjectMetadataDtoMapper;
import org.vader.common.model.vader.dto.ObjectMetadata;
import org.vader.common.model.vader.entity.ObjectMetadataEntity;

/** Read-only query API for {@link ObjectMetadataEntity} (stored-file metadata, not contents). */
@RestController
@RequestMapping("/vader/core-server/data/object-metadata")
public class ObjectMetadataDaoController
    extends GenericVaderDaoController<ObjectMetadataEntity, ObjectMetadata> {

    @Autowired
    private EntityDao<ObjectMetadataEntity> entityDao;

    @Autowired
    private ObjectMetadataDtoMapper dtoMapper;

    @Override
    public EntityDao<ObjectMetadataEntity> getDataAccessObject() {
        return this.entityDao;
    }

    @Override
    public InterfaceEntityToDtoMapper<ObjectMetadataEntity, ObjectMetadata>
        getDataTransferObjectMapper() {
        return this.dtoMapper;
    }
}
