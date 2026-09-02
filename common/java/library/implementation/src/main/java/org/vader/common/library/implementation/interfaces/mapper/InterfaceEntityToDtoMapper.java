package org.vader.common.library.implementation.interfaces.mapper;

import java.util.List;
import java.util.Set;
import org.vader.common.model.vader.dto.AbstractModel;
import org.vader.common.model.vader.entity.AbstractModelEntity;

/**
 * Maps persisted JPA entities to their Data Transfer Object representations.
 *
 * @param <F> the entity type to map from
 * @param <T> the DTO type to map to
 */
public interface InterfaceEntityToDtoMapper<
    F extends AbstractModelEntity,
    T extends AbstractModel> {

    /**
     * Maps a single entity to its DTO.
     *
     * @param from the entity to map from
     * @return the mapped DTO, or {@code null} if {@code from} is {@code null}
     */
    T map(final F from);

    /**
     * Maps a list of entities to a list of DTOs.
     *
     * @param froms the entities to map from
     * @return the mapped DTOs, never {@code null}
     */
    List<T> map(final List<F> froms);

    /**
     * Maps a set of entities to a list of DTOs.
     *
     * @param froms the entities to map from
     * @return the mapped DTOs, never {@code null}
     */
    List<T> map(final Set<F> froms);
}
