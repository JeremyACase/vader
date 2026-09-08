package org.vader.core.server.tools.query;

import jakarta.annotation.PostConstruct;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.vader.common.library.dao.controller.GenericVaderDaoController;

/**
 * Indexes the registered {@link GenericVaderDaoController}s by their DTO name (e.g.
 * {@code "Workflow"}, {@code "Task"}) so the query service and MCP tools can resolve an entity by
 * name. Only entities with a DAO controller are reachable — {@code FileContentEntity} has none.
 */
@Component
public class VaderDaoRegistry {

    @Autowired
    private List<GenericVaderDaoController<?, ?>> controllers;

    private final Map<String, GenericVaderDaoController<?, ?>> byName = new LinkedHashMap<>();

    @PostConstruct
    void index() {
        for (var controller : this.controllers) {
            this.byName.put(controller.getDtoClass().getSimpleName(), controller);
        }
    }

    /**
     * Returns every queryable entity name.
     *
     * @return the entity names
     */
    public Set<String> names() {
        return this.byName.keySet();
    }

    /**
     * Resolves a queryable entity by name.
     *
     * @param name the entity name (case-sensitive DTO simple name)
     * @return its DAO controller
     * @throws IllegalArgumentException if no entity is registered under {@code name}
     */
    public GenericVaderDaoController<?, ?> require(final String name) {
        var controller = this.byName.get(name);
        if (controller == null) {
            throw new IllegalArgumentException(
                "Unknown entity '" + name + "'. Queryable entities: " + this.byName.keySet());
        }
        return controller;
    }
}
