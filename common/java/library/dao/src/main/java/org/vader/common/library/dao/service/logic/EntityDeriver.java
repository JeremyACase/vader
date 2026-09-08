package org.vader.common.library.dao.service.logic;

import jakarta.persistence.EntityManager;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Determines, and caches, whether a class is registered as a JPA {@code @Entity}.
 */
@Service
public class EntityDeriver {

    @Autowired
    private EntityManager entityManager;

    private final Map<Class<?>, Boolean> cache = new HashMap<>();

    /**
     * Returns whether {@code clazz} is a managed JPA entity.
     *
     * @param clazz the class to check
     * @return {@code true} if it is an entity
     */
    public boolean isEntityClass(final Class<?> clazz) {
        return this.cache.computeIfAbsent(clazz, key ->
            this.entityManager.getMetamodel().getEntities().stream()
                .anyMatch(entityType -> entityType.getJavaType().equals(key)));
    }
}
