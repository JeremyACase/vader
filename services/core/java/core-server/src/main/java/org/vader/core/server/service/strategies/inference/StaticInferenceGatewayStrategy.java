package org.vader.core.server.service.strategies.inference;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.vader.core.server.models.ConversationMessage;
import org.vader.core.server.models.ConversationRole;
import org.vader.core.server.models.InferenceToolCall;
import org.vader.core.server.models.InferenceTurn;
import org.vader.core.server.service.agent.orchestrator.strategies.StaticLlmOrchestrationStrategy;
import org.vader.core.server.service.registries.AgentToolAudience;
import org.vader.core.server.service.registries.McpToolCallbackRegistry;

/**
 * Returns a fixed, scripted turn sequence -- no LLM, no network call. Active whenever
 * {@code vader.orchestrator.type} is {@code static}, mirroring
 * {@code StaticLlmOrchestrationStrategy}: the same switch that keeps decomposition deterministic
 * for {@code helm test} / CI also keeps every harness inference call deterministic, with no
 * Ollama in the cluster.
 *
 * <p>The very first turn always requests {@code list_queryable_entities} (a harmless,
 * argument-free tool that's always registered) instead of answering directly, so a real harness
 * running against this strategy deterministically exercises its full action loop -- including
 * actually invoking a tool via {@code /agent/tool-calls} -- end to end in {@code helm test}, with
 * no live LLM involved. Every subsequent turn -- detected by the conversation already containing
 * a {@code TOOL} message, i.e. the harness already folded a result back in -- returns the canned
 * final answer.</p>
 */
@Service
@ConditionalOnProperty(prefix = "vader.orchestrator", name = "type", havingValue = "static")
public class StaticInferenceGatewayStrategy implements InterfaceInferenceGatewayStrategy {

    private static final Logger logger =
        LoggerFactory.getLogger(StaticInferenceGatewayStrategy.class);

    private static final String CANNED_RESPONSE =
        "Static inference response (vader.orchestrator.type=static; no LLM was called).";

    private static final String SCRIPTED_TOOL_CALL_ID = "static-scripted-call-1";

    private static final String SCRIPTED_TOOL_NAME = "list_queryable_entities";

    private static final long CANNED_TOKEN_COST = 10L;

    @Autowired
    private McpToolCallbackRegistry toolCallbackRegistry;

    @Override
    public InferenceTurn complete(final List<ConversationMessage> messages) {
        logger.debug(
            "Static inference turn requested with {} tool(s) registered",
            this.toolCallbackRegistry.forAudience(AgentToolAudience.TASK_EXECUTION).size());
        return hasToolResult(messages) ? finalTurn() : scriptedToolCallTurn();
    }

    private static boolean hasToolResult(final List<ConversationMessage> messages) {
        return messages.stream().anyMatch(message -> message.role() == ConversationRole.TOOL);
    }

    private static InferenceTurn finalTurn() {
        return new InferenceTurn(CANNED_RESPONSE, List.of(), CANNED_TOKEN_COST);
    }

    private static InferenceTurn scriptedToolCallTurn() {
        var toolCall = new InferenceToolCall(SCRIPTED_TOOL_CALL_ID, SCRIPTED_TOOL_NAME, "{}");
        return new InferenceTurn(null, List.of(toolCall), CANNED_TOKEN_COST);
    }
}
