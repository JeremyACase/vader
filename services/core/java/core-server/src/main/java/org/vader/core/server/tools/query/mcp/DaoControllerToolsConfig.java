package org.vader.core.server.tools.query.mcp;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.support.TransactionTemplate;
import org.vader.common.library.dao.controller.GenericVaderDaoController;
import org.vader.common.library.dao.model.QueryFilter;

/**
 * Programmatically registers per-entity MCP tools for every {@link GenericVaderDaoController}
 * bean. Each entity gets three tools — {@code query_{entity}}, {@code count_{entity}}, and
 * {@code get_{entity}_by_id} — so agents can query the database at the entity level without
 * needing to know entity names up front.
 *
 * <p>Gated on the same property as {@link DatabaseQueryToolsConfig} so both sets of tools are
 * enabled and disabled together.</p>
 */
@Configuration
@ConditionalOnProperty(
    prefix = "vader.mcp.database-query",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class DaoControllerToolsConfig {

    @Autowired
    private List<GenericVaderDaoController<?, ?>> daoControllers;

    @Autowired
    private TransactionTemplate transactionTemplate;

    /** Input type for the per-entity get-by-id MCP tools. */
    public record IdInput(String id) {}

    /**
     * Produces one {@link ToolCallbackProvider} covering all registered DAO controllers.
     *
     * @return the compound provider
     */
    @Bean
    @SuppressWarnings({"unchecked", "rawtypes"})
    public ToolCallbackProvider daoControllerToolCallbacks() {
        var callbacks = this.daoControllers.stream()
            .flatMap(ctrl -> this.toolsFor((GenericVaderDaoController) ctrl).stream())
            .toArray(ToolCallback[]::new);
        return () -> callbacks;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private List<ToolCallback> toolsFor(final GenericVaderDaoController ctrl) {
        var entityName = ctrl.getEntityClass().getSimpleName()
            .replaceAll("(?i)Entity$", "")
            .toLowerCase();
        return List.of(
            this.buildQueryCallback(ctrl, entityName),
            this.buildCountCallback(ctrl, entityName),
            this.buildGetByIdCallback(ctrl, entityName));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private ToolCallback buildQueryCallback(final GenericVaderDaoController ctrl,
            final String entityName) {
        return FunctionToolCallback.builder("query_" + entityName, (QueryFilter filter) ->
                this.transactionTemplate.execute(status -> {
                    try {
                        return ctrl.toDtoPage(ctrl.getDataAccessObject().getPage(
                            filter, filter.getPage(), filter.getPageSize(),
                            ctrl.getEntityClass()));
                    } catch (NoSuchFieldException e) {
                        throw new RuntimeException(e.getMessage(), e);
                    }
                }))
            .description("Query paginated " + entityName + " records with AND-combined filter "
                + "constraints. Each parameter has a key (field name or dot-notation path), "
                + "operator (EQUAL, LIKE, GREATER_THAN, GREATER_THAN_OR_EQUAL_TO, LESS_THAN, "
                + "LESS_THAN_OR_EQUAL_TO), and string value. Returns a page of DTOs.")
            .inputType(QueryFilter.class)
            .build();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private ToolCallback buildCountCallback(final GenericVaderDaoController ctrl,
            final String entityName) {
        return FunctionToolCallback.builder("count_" + entityName, (QueryFilter filter) ->
                this.transactionTemplate.execute(status -> {
                    try {
                        return Map.of("count",
                            ctrl.getDataAccessObject().getCount(filter, ctrl.getEntityClass()));
                    } catch (NoSuchFieldException e) {
                        throw new RuntimeException(e.getMessage(), e);
                    }
                }))
            .description("Count " + entityName + " records matching a filter without fetching "
                + "them. Uses the same filter shape as query_" + entityName + ".")
            .inputType(QueryFilter.class)
            .build();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private ToolCallback buildGetByIdCallback(final GenericVaderDaoController ctrl,
            final String entityName) {
        return FunctionToolCallback.builder("get_" + entityName + "_by_id", (IdInput q) ->
                this.transactionTemplate.execute(status -> {
                    try {
                        var params = new HashMap<String, String[]>();
                        params.put("id", new String[]{q.id()});
                        var records = ctrl.getDataAccessObject().getPage(
                            params, 0, 1, true, new ArrayList<>(), ctrl.getEntityClass());
                        return records.isEmpty()
                            ? null
                            : ctrl.toDtoPage(records).getContent().get(0);
                    } catch (NoSuchFieldException e) {
                        throw new RuntimeException(e.getMessage(), e);
                    }
                }))
            .description("Fetch one " + entityName + " by its id. Returns the DTO, or null "
                + "if not found.")
            .inputType(IdInput.class)
            .build();
    }
}
