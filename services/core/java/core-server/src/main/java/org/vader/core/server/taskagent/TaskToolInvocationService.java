package org.vader.core.server.taskagent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.vader.common.model.vader.entity.TaskAttemptEntity;
import org.vader.common.model.vader.entity.TaskAttemptToolCallEntity;
import org.vader.core.server.mcp.McpToolCallbackRegistry;
import org.vader.core.server.taskagent.model.ToolCallInvocationResult;

/**
 * Executes the tool calls a model requests during an assignment's inference turns, scoped to the
 * calling task and written to the tool-call audit trail before the result is returned.
 */
@Service
public class TaskToolInvocationService {

    private static final String POST_TASK_UPDATE_TOOL_NAME = "post_task_update";

    private static final String TASK_ID_ARGUMENT_KEY = "taskId";

    private static final String TASK_ATTEMPT_ID_ARGUMENT_KEY = "taskAttemptId";

    @Autowired
    private TaskAttemptLifecycleService lifecycleService;

    @Autowired
    private McpToolCallbackRegistry toolCallbackRegistry;

    @Autowired
    private ToolCallInvocationBoundary toolCallInvocationBoundary;

    @Autowired
    private TaskAttemptToolCallRepository toolCallRepository;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * Executes one tool call a model requested, on behalf of an assignment, and writes it to the
     * tool-call audit trail before returning -- so the call survives even if the harness never
     * calls back again. Uses the same {@link McpToolCallbackRegistry} lookup an MCP client would.
     *
     * <p>An unknown tool is audited, with the error as its result, before
     * {@link UnknownToolException} is thrown. A tool that throws becomes an ordinary error result
     * the model can reason about, not a 500 the harness would mistake for a connectivity failure.
     * The call runs through {@link ToolCallInvocationBoundary} so a failing
     * {@code @Transactional} tool cannot mark this transaction rollback-only.</p>
     *
     * @param assignmentId the calling harness's assignment id
     * @param toolCallId the id correlating this invocation back to the model's request
     * @param toolName the tool to invoke
     * @param argumentsJson the tool's arguments, as a JSON object string
     * @return the tool's raw result
     */
    @Transactional
    public ToolCallInvocationResult invokeTool(
            final String assignmentId, final String toolCallId, final String toolName,
            final String argumentsJson) {
        var attempt = this.lifecycleService.requireOpen(assignmentId);

        var scopedArgumentsJson = this.scopedToOwnTask(toolName, argumentsJson, attempt);
        var toolCallback = this.toolCallbackRegistry.findByName(toolName);
        var resultJson = toolCallback.isPresent()
            ? this.invoke(toolCallback.get(), scopedArgumentsJson, attempt.getId())
            : this.toJson(Map.of("error", unknownToolMessage(toolName)));
        this.recordToolCall(attempt, toolCallId, toolName, scopedArgumentsJson, resultJson);

        if (toolCallback.isEmpty()) {
            throw new UnknownToolException(unknownToolMessage(toolName));
        }
        return new ToolCallInvocationResult(toolCallId, resultJson);
    }

    /**
     * Overwrites a {@code taskId}/{@code taskAttemptId} argument with the calling assignment's own
     * task and attempt, for any tool that must never reach outside its own task (today, only
     * {@code post_task_update}) -- matching {@code AgentToolAudience.TASK_EXECUTION}'s documented
     * invariant. Whatever the model itself supplied for either id is discarded rather than merely
     * validated, so a hallucinated or malicious id can never take effect, not just be rejected
     * after the fact.
     *
     * @param toolName the tool about to be invoked
     * @param argumentsJson the model-supplied arguments, as a JSON object string
     * @param attempt the calling assignment
     * @return {@code argumentsJson} unchanged, unless {@code toolName} needs task-scoping
     */
    private String scopedToOwnTask(
            final String toolName, final String argumentsJson, final TaskAttemptEntity attempt) {
        var result = argumentsJson;
        if (POST_TASK_UPDATE_TOOL_NAME.equals(toolName)) {
            result = this.withOwnTaskAndAttemptId(
                argumentsJson, attempt.getTask().getId(), attempt.getId());
        }
        return result;
    }

    private String withOwnTaskAndAttemptId(
            final String argumentsJson, final String taskId, final String taskAttemptId) {
        Map<String, Object> arguments;
        try {
            arguments = this.objectMapper.readValue(
                argumentsJson, new TypeReference<Map<String, Object>>() {});
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not scope tool arguments to the calling task",
                e);
        }
        var scoped = new LinkedHashMap<>(arguments);
        scoped.put(TASK_ID_ARGUMENT_KEY, taskId);
        scoped.put(TASK_ATTEMPT_ID_ARGUMENT_KEY, taskAttemptId);
        return this.toJson(scoped);
    }

    private String invoke(
            final ToolCallback toolCallback, final String argumentsJson,
            final String taskAttemptId) {
        String resultJson;
        try {
            resultJson = this.toolCallInvocationBoundary.invoke(
                toolCallback, argumentsJson, TaskAttemptToolContext.of(taskAttemptId));
        } catch (RuntimeException e) {
            resultJson = this.toJson(Map.of("error", "Tool call failed: " + e.getMessage()));
        }
        return resultJson;
    }

    private static String unknownToolMessage(final String toolName) {
        return "No tool registered with name '" + toolName + "'";
    }

    private void recordToolCall(
            final TaskAttemptEntity attempt, final String toolCallId, final String toolName,
            final String argumentsJson, final String resultJson) {
        var record = new TaskAttemptToolCallEntity();
        record.setTaskAttempt(attempt);
        record.setToolCallId(toolCallId);
        record.setToolName(toolName);
        record.setArgumentsJson(argumentsJson);
        record.setResultJson(resultJson);
        this.toolCallRepository.save(record);
    }

    private String toJson(final Object value) {
        String result;
        try {
            result = this.objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize tool-call content", e);
        }
        return result;
    }
}
