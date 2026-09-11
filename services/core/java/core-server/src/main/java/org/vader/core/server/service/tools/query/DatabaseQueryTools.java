package org.vader.core.server.service.tools.query;

import java.util.List;
import java.util.Map;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.vader.common.library.dao.model.QueryFilter;
import org.vader.core.server.models.EntityDescription;
import org.vader.core.server.service.query.DatabaseQueryService;

/**
 * Exposes read-only access to the vader database to LLMs as MCP tools: discover the schema with
 * {@code list_queryable_entities}, then filter/paginate with {@code query_database} /
 * {@code count_matching}. Every result is a DTO, so heavy/sensitive fields (file bytes) are
 * already stripped, and only the six registered entities are reachable.
 */
@Component
@ConditionalOnProperty(
    prefix = "vader.mcp.database-query",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class DatabaseQueryTools {

    @Autowired
    private DatabaseQueryService service;

    /**
     * Lists the queryable entities and, for each, the fields a filter may constrain.
     *
     * @return one description per entity
     */
    @Tool(
        name = "list_queryable_entities",
        description = "List the vader database entities that can be queried and, for each, its "
            + "filterable fields. Fields typed '-> X' or '-> [X]' are associations: reach them "
            + "with dot notation, e.g. key 'taskPlan.objective'. Call this before query_database.")
    public List<EntityDescription> listQueryableEntities() {
        return this.service.describe();
    }

    /**
     * Runs a structured, paginated query against one entity.
     *
     * @param entity the entity name from {@link #listQueryableEntities()}
     * @param filter the filter (AND-combined parameters, sort, page, pageSize)
     * @return a page of matching rows, or {@code {"error": ...}} if the filter is invalid
     */
    @Tool(
        name = "query_database",
        description = "Query one vader entity. 'entity' is a name from list_queryable_entities. "
            + "'filter.parameters' are AND-combined; each has key (field name or dotted "
            + "keychain), operator (EQUAL, LIKE, GREATER_THAN, GREATER_THAN_OR_EQUAL_TO, "
            + "LESS_THAN, LESS_THAN_OR_EQUAL_TO), and value (a string; use % wildcards with "
            + "LIKE, or 'null'/'!null' to test nullness). Returns a page of DTOs.")
    public Object queryDatabase(
        @ToolParam(description = "entity name from list_queryable_entities")
        final String entity,
        @ToolParam(description = "the structured filter")
        final QueryFilter filter) {
        try {
            return this.service.query(entity, filter);
        } catch (NoSuchFieldException | RuntimeException e) {
            return Map.of("error", String.valueOf(e.getMessage()));
        }
    }

    /**
     * Counts the rows matching a filter, without returning them.
     *
     * @param entity the entity name
     * @param filter the filter
     * @return {@code {"count": n}} or {@code {"error": ...}}
     */
    @Tool(
        name = "count_matching",
        description = "Count rows of one vader entity matching a filter (same filter shape as "
            + "query_database), without returning the rows.")
    public Object countMatching(
        @ToolParam(description = "entity name from list_queryable_entities")
        final String entity,
        @ToolParam(description = "the structured filter")
        final QueryFilter filter) {
        try {
            return Map.of("count", this.service.count(entity, filter));
        } catch (NoSuchFieldException | RuntimeException e) {
            return Map.of("error", String.valueOf(e.getMessage()));
        }
    }
}
