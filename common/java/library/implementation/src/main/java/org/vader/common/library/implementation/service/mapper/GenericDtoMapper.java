package org.vader.common.library.implementation.service.mapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.vader.common.library.implementation.interfaces.mapper.InterfaceEntityToDtoMapper;
import org.vader.common.model.vader.dto.AbstractModel;
import org.vader.common.model.vader.entity.AbstractModelEntity;

/**
 * Base class for entity-to-DTO mappers, providing the collection overloads and the copy of the
 * identity and audit fields common to every model.
 *
 * @param <F> the entity type to map from
 * @param <T> the DTO type to map to
 */
public abstract class GenericDtoMapper<
    F extends AbstractModelEntity,
    T extends AbstractModel>
    implements InterfaceEntityToDtoMapper<F, T> {

    @Override
    public List<T> map(final List<F> froms) {
        var tos = new ArrayList<T>();
        if (Objects.nonNull(froms)) {
            for (var from : froms) {
                tos.add(this.map(from));
            }
        }
        return tos;
    }

    @Override
    public List<T> map(final Set<F> froms) {
        var tos = new ArrayList<T>();
        if (Objects.nonNull(froms)) {
            for (var from : froms) {
                tos.add(this.map(from));
            }
        }
        return tos;
    }

    /**
     * Copies the identity and audit fields that are present on every model.
     *
     * @param from the entity to copy from
     * @param to the DTO to copy to
     */
    protected void setAbstractModelFields(final F from, final T to) {
        if (Objects.nonNull(from) && Objects.nonNull(to)) {
            to.setId(from.getId());
            to.setCreatedAt(from.getCreatedAt());
            to.setUpdatedAt(from.getUpdatedAt());
        }
    }
}
