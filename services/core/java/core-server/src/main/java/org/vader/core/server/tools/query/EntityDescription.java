package org.vader.core.server.tools.query;

import java.util.List;

/**
 * The queryable shape of one entity, returned to LLMs so they can build valid filters.
 *
 * @param name the entity name to pass to query tools (e.g. {@code "Workflow"})
 * @param description a one-line description of what the entity represents
 * @param fields the fields that can appear in a filter's {@code key}
 */
public record EntityDescription(String name, String description, List<Field> fields) {

    /**
     * One filterable field of an entity.
     *
     * @param name the field name (use directly, or as a dotted keychain segment)
     * @param type the field's type — a scalar type, or {@code "-> <Entity>"} for an association
     *     that must be reached with dot notation (e.g. {@code taskPlan.objective})
     */
    public record Field(String name, String type) {
    }
}
