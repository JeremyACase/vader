package org.vader.common.library.implementation.interfaces.mapper;

import org.vader.common.model.vader.dto.AbstractModel;
import org.vader.common.model.vader.entity.AbstractModelEntity;

/**
 * Maps an ingress Data Transfer Object to a transient, persistable JPA entity.
 *
 * <p>Implementations build a fresh entity graph -- identity and audit fields are left for the
 * persistence provider to assign -- and wire up the bidirectional associations that JPA needs in
 * order to cascade the graph on save.</p>
 *
 * @param <F> the DTO type to map from
 * @param <T> the entity type to map to
 */
public interface InterfaceDtoToEntityMapper<
    F extends AbstractModel,
    T extends AbstractModelEntity> {

    /**
     * Maps a single DTO to a transient entity graph.
     *
     * @param from the DTO to map from
     * @return the mapped entity, or {@code null} if {@code from} is {@code null}
     */
    T map(final F from);
}
