package org.vader.common.library.dao.interfaces;

import org.vader.common.library.dao.component.EntityDao;
import org.vader.common.library.implementation.interfaces.mapper.InterfaceEntityToDtoMapper;
import org.vader.common.model.vader.dto.AbstractModel;
import org.vader.common.model.vader.entity.AbstractModelEntity;

/**
 * Contract a concrete DAO-backed controller supplies to {@link
 * org.vader.common.library.dao.controller.GenericVaderDaoController}: its entity DAO and its
 * entity-to-DTO mapper.
 *
 * @param <T> the entity type
 * @param <D> the DTO type
 */
public interface InterfaceVaderDaoController<
    T extends AbstractModelEntity,
    D extends AbstractModel> {

    /**
     * Returns the DAO for the managed entity type.
     *
     * @return the entity DAO
     */
    EntityDao<T> getDataAccessObject();

    /**
     * Returns the mapper that converts managed entities to their DTOs.
     *
     * @return the entity-to-DTO mapper
     */
    InterfaceEntityToDtoMapper<T, D> getDataTransferObjectMapper();
}
