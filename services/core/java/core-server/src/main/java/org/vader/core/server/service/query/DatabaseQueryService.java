package org.vader.core.server.service.query;

import jakarta.persistence.Transient;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.vader.common.library.dao.model.QueryFilter;
import org.vader.common.model.vader.entity.AbstractModelEntity;
import org.vader.core.server.models.EntityDescription;
import org.vader.core.server.models.QueryResult;
import org.vader.core.server.service.registries.VaderDaoRegistry;

/**
 * The shared read-only query layer over the DAO controllers, used by both the REST endpoints
 * (indirectly, via the controllers) and the MCP tools. Enabled by default; disable with
 * {@code vader.mcp.database-query.enabled=false}.
 */
@Service
@ConditionalOnProperty(
    prefix = "vader.mcp.database-query",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class DatabaseQueryService {

    private static final Set<Class<?>> SCALAR_TYPES = Set.of(
        String.class, Boolean.class, Integer.class, Long.class, Short.class,
        Float.class, Double.class, BigDecimal.class, OffsetDateTime.class, UUID.class);

    private static final Map<String, String> DESCRIPTIONS = Map.of(
        "Workflow", "A run spawned to service one client prompt; owns a task plan.",
        "ClientPrompt", "A prompt a client submitted (attached file bytes are never returned).",
        "TaskPlan", "The orchestrator's decomposition of a prompt into an objective + task graph.",
        "TaskGraph", "The DAG of tasks belonging to a task plan.",
        "Task", "A single node of a task graph, with title, description, parent and dependencies.",
        "ObjectMetadata", "Metadata for a stored file (name, content type, size) — not its bytes.");

    @Autowired
    private VaderDaoRegistry registry;

    /**
     * Describes every queryable entity and its filterable fields.
     *
     * @return one description per registered entity
     */
    public List<EntityDescription> describe() {
        return this.registry.names().stream()
            .map(name -> new EntityDescription(
                name,
                DESCRIPTIONS.getOrDefault(name, name),
                this.fieldsOf(this.registry.require(name).getEntityClass())))
            .toList();
    }

    /**
     * Runs a structured query against the named entity and returns a page of DTOs.
     *
     * @param entity the entity name (see {@link #describe()})
     * @param filter the structured filter
     * @return the matching page
     * @throws NoSuchFieldException if the filter references a field the entity does not have
     */
    public QueryResult query(final String entity, final QueryFilter filter)
        throws NoSuchFieldException {
        var page = this.registry.require(entity).query(filter);
        return new QueryResult(
            page.getContent(),
            page.getTotalElements(),
            page.getNumber(),
            page.getSize(),
            page.getTotalPages());
    }

    /**
     * Counts the rows matching a structured query against the named entity.
     *
     * @param entity the entity name
     * @param filter the structured filter
     * @return the match count
     * @throws NoSuchFieldException if the filter references a field the entity does not have
     */
    public long count(final String entity, final QueryFilter filter) throws NoSuchFieldException {
        return this.registry.require(entity).count(filter).getOrDefault("count", 0L);
    }

    private List<EntityDescription.Field> fieldsOf(final Class<?> entityClass) {
        return FieldUtils.getAllFieldsList(entityClass).stream()
            .filter(field -> !Modifier.isStatic(field.getModifiers()))
            .filter(field -> !field.isSynthetic())
            .filter(field -> !field.isAnnotationPresent(Transient.class))
            .filter(field -> !"modelType".equals(field.getName()))
            .map(field -> {
                var type = field.getType();
                if (type.isEnum() || SCALAR_TYPES.contains(type)) {
                    return new EntityDescription.Field(field.getName(), type.getSimpleName());
                }
                if (AbstractModelEntity.class.isAssignableFrom(type)) {
                    return new EntityDescription.Field(field.getName(),
                        "-> " + type.getSimpleName().replace("Entity", ""));
                }
                if (Collection.class.isAssignableFrom(type)
                    && field.getGenericType() instanceof ParameterizedType parameterized) {
                    var element = (Class<?>) parameterized.getActualTypeArguments()[0];
                    return new EntityDescription.Field(field.getName(),
                        "-> [" + element.getSimpleName().replace("Entity", "") + "]");
                }
                return null;
            })
            .filter(field -> field != null)
            .toList();
    }
}
