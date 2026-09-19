package org.vader.core.server.service.tools.backpressure;

import java.util.Map;
import java.util.Set;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.vader.core.server.service.backpressure.BackpressureCalculator;
import org.vader.core.server.service.registries.BackpressureRegistry;

/**
 * Exposes the inbox/outbox back pressure to LLMs as MCP tools: discover the queues with
 * {@code list_backpressure_queues}, then read one with {@code get_backpressure}. The individual
 * queue messages are never exposed -- only the aggregate depth, rate, and in-flight counts.
 */
@Service
@ConditionalOnProperty(
    prefix = "vader.mcp.backpressure",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = false)
public class BackpressureTools {

    @Autowired
    private BackpressureRegistry registry;

    @Autowired
    private BackpressureCalculator calculator;

    /**
     * Lists the model types that have an inbox/outbox queue.
     *
     * @return the queued model-type names
     */
    @Tool(
        name = "list_backpressure_queues",
        description = "List the model types that have an inbox/outbox queue whose back pressure "
            + "can be read with get_backpressure (e.g. 'ClientPrompt').")
    public Set<String> listBackpressureQueues() {
        return this.registry.names();
    }

    /**
     * Returns the current back pressure snapshot for one queued model type.
     *
     * @param modelType a name from {@link #listBackpressureQueues()}
     * @return the snapshot, or {@code {"error": ...}} for an unknown model type
     */
    @Tool(
        name = "get_backpressure",
        description = "Read the current back pressure for one queued model type. Returns "
            + "'ingress' (queuedRecords waiting, queueRatePerMinute rate of change) and 'egress' "
            + "(maxOpenMessages ceiling, currentOpenMessages in flight).")
    public Object getBackpressure(
        @ToolParam(description = "model type name from list_backpressure_queues")
        final String modelType) {
        try {
            return this.calculator.calculateFor(modelType);
        } catch (IllegalArgumentException e) {
            return Map.of("error", String.valueOf(e.getMessage()));
        }
    }
}
