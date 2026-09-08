package org.vader.common.library.dao.service.logic;

import jakarta.persistence.EntityManager;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Determines, and caches, whether a class is registered as a JPA {@code @Embeddable}.
 */
@Service
public class EmbeddableDeriver {

    @Autowired
    private EntityManager entityManager;

    private final Map<Class<?>, Boolean> cache = new HashMap<>();

    /**
     * Returns whether {@code clazz} is a JPA embeddable type.
     *
     * @param clazz the class to check
     * @return {@code true} if it is an embeddable
     */
    public boolean isEmbeddedClass(final Class<?> clazz) {
        return this.cache.computeIfAbsent(clazz, key ->
            this.entityManager.getMetamodel().getEmbeddables().stream()
                .anyMatch(embeddableType -> embeddableType.getJavaType().equals(key)));
    }
}
