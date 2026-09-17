package org.vader.core.server.service.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.vader.common.model.vader.entity.ClientPromptEntity;
import org.vader.common.model.vader.entity.TaskAttemptEntity;
import org.vader.common.model.vader.entity.TaskAttemptStatus;
import org.vader.common.model.vader.entity.TaskAttemptToolCallEntity;
import org.vader.common.model.vader.entity.TaskAttemptTranscriptEntity;
import org.vader.common.model.vader.entity.TaskEntity;
import org.vader.core.server.exceptions.AssignmentAlreadyTerminalException;
import org.vader.core.server.exceptions.UnknownAssignmentException;
import org.vader.core.server.exceptions.UnknownToolException;
import org.vader.core.server.models.AgentHarnessSpec;
import org.vader.core.server.models.AssignmentResponse;
import org.vader.core.server.models.ConversationMessage;
import org.vader.core.server.models.HeartbeatRequest;
import org.vader.core.server.models.InferenceTurn;
import org.vader.core.server.models.ResultRequest;
import org.vader.core.server.models.TaskAttemptSettledEvent;
import org.vader.core.server.models.ToolCallInvocationResult;
import org.vader.core.server.repository.TaskAttemptRepository;
import org.vader.core.server.repository.TaskAttemptToolCallRepository;
import org.vader.core.server.repository.TaskAttemptTranscriptRepository;
import org.vader.core.server.service.registries.McpToolCallbackRegistry;
import org.vader.core.server.service.strategies.inference.InterfaceInferenceGatewayStrategy;

/**
 * Owns every status transition a {@code TaskAttempt} goes through, from creation through
 * dispatch to a terminal outcome -- the durable audit trail behind "log everything from prompt to
 * workflow finished." Backs both {@code TaskAssignmentInbox} (dispatch bookkeeping) and
 * {@code AgentAssignmentController} (the harness-facing control plane and inference gateway).
 *
 * <p>Every public method here re-fetches the attempt fresh by id rather than accepting a
 * caller-held reference, the same reasoning {@code QueueMessageProcessor} documents: each call is
 * its own independent, short transaction.</p>
 */
@Service
public class TaskAttemptService {

    @Autowired
    private TaskAttemptRepository taskAttemptRepository;

    @Autowired
    private TaskAttemptTranscriptRepository transcriptRepository;

    @Autowired
    private TaskAttemptToolCallRepository toolCallRepository;

    @Autowired
    private InterfaceInferenceGatewayStrategy inferenceGateway;

    @Autowired
    private McpToolCallbackRegistry toolCallbackRegistry;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${vader.agent-harness.max-turns:20}")
    private int maxTurns;

    @Value("${vader.agent-harness.max-tokens:200000}")
    private long maxTokens;

    @Value("${vader.agent-harness.deadline-seconds:600}")
    private long deadlineSeconds;

    /**
     * Resolves the task/assignment identity a Job manifest needs, for {@code TaskAssignmentInbox}
     * to dispatch.
     *
     * @param assignmentId the attempt id about to be dispatched
     * @return the resolved spec
     */
    @Transactional
    public AgentHarnessSpec specFor(final String assignmentId) {
        var attempt = this.require(assignmentId);
        return new AgentHarnessSpec(attempt.getTask().getId(), attempt.getId());
    }

    /**
     * Records that a harness Job was successfully created for this attempt.
     *
     * @param assignmentId the dispatched attempt id
     */
    @Transactional
    public void markDispatched(final String assignmentId) {
        var attempt = this.require(assignmentId);
        attempt.setStatus(TaskAttemptStatus.DISPATCHED);
        attempt.setDispatchedAt(OffsetDateTime.now());
        this.taskAttemptRepository.save(attempt);
    }

    /**
     * Records that dispatch itself failed -- no Job was ever created, so this attempt can never
     * report back on its own. Settles it as {@code FAILED} immediately.
     *
     * @param assignmentId the attempt id that failed to dispatch
     * @param reason why dispatch failed
     */
    @Transactional
    public void markDispatchFailed(final String assignmentId, final String reason) {
        var attempt = this.require(assignmentId);
        this.settle(attempt, TaskAttemptStatus.FAILED, null, reason);
    }

    /**
     * Fetches the work order for an assignment, marking the attempt {@code RUNNING} on first
     * contact.
     *
     * @param assignmentId the calling harness's assignment id
     * @return the work order
     */
    @Transactional
    public AssignmentResponse fetchAssignment(final String assignmentId) {
        var attempt = this.require(assignmentId);
        this.rejectIfTerminal(attempt);

        if (attempt.getStatus() != TaskAttemptStatus.RUNNING) {
            attempt.setStatus(TaskAttemptStatus.RUNNING);
            attempt.setStartedAt(OffsetDateTime.now());
            attempt.setLastHeartbeatAt(OffsetDateTime.now());
            this.taskAttemptRepository.save(attempt);
        }

        var task = attempt.getTask();
        return new AssignmentResponse(
            task.getId(),
            attempt.getId(),
            task.getDescription(),
            this.contextFor(task),
            this.maxTurns,
            this.maxTokens,
            this.deadlineSeconds);
    }

    /**
     * Composes the background a task's own short description never carries on its own: the
     * original client-submitted request, any files attached to it, and the results of any
     * prerequisite tasks -- without this, a task like "ensure the report is well-structured" has
     * no way to discover what report, and a task like "identify patterns in the data" has no way
     * to discover what data, since neither is restated in every subtask by the planner.
     *
     * @param task the task about to be dispatched
     * @return the composed context, always at least the original request
     */
    private String contextFor(final TaskEntity task) {
        var clientPrompt =
            task.getTaskGraph().getTaskPlan().getWorkflow().getClientPrompt();
        var sections = Stream.of(
                requestSection(clientPrompt),
                attachedFilesSection(clientPrompt),
                this.dependencySection(task))
            .filter(section -> section != null)
            .toList();
        return String.join("\n\n", sections);
    }

    private static String requestSection(final ClientPromptEntity clientPrompt) {
        return "Original request from the user:\n" + clientPrompt.getText();
    }

    private static String attachedFilesSection(final ClientPromptEntity clientPrompt) {
        String result = null;
        if (!clientPrompt.getFiles().isEmpty()) {
            var lines = clientPrompt.getFiles().stream()
                .map(file -> "- \"" + file.getOriginalFilename() + "\" (id: " + file.getId()
                    + ", type: " + file.getContentType()
                    + ") -- fetch its content with get_object_content.")
                .toList();
            result = "Files attached to the original request:\n" + String.join("\n", lines);
        }
        return result;
    }

    private String dependencySection(final TaskEntity task) {
        String result = null;
        if (!task.getDependsOn().isEmpty()) {
            var lines = task.getDependsOn().stream().map(this::dependencyLine).toList();
            result = "Results from prerequisite tasks this one depends on:\n"
                + String.join("\n", lines);
        }
        return result;
    }

    private String dependencyLine(final TaskEntity dependency) {
        var result = this.taskAttemptRepository
            .findFirstByTaskIdOrderByAttemptNumberDesc(dependency.getId())
            .map(TaskAttemptEntity::getResult)
            .filter(text -> text != null)
            .orElse("(no result recorded)");
        return "- \"" + dependency.getTitle() + "\": " + result;
    }

    /**
     * Records a liveness/progress report partway through a run.
     *
     * @param assignmentId the calling harness's assignment id
     * @param request the reported progress
     */
    @Transactional
    public void recordHeartbeat(final String assignmentId, final HeartbeatRequest request) {
        var attempt = this.require(assignmentId);
        this.rejectIfTerminal(attempt);
        attempt.setTurnsUsed(request.turnsUsed());
        attempt.setTokensUsed(request.tokensUsed());
        attempt.setLastHeartbeatAt(OffsetDateTime.now());
        this.taskAttemptRepository.save(attempt);
    }

    /**
     * Records an attempt's terminal outcome and notifies the scheduler that this task settled.
     *
     * @param assignmentId the calling harness's assignment id
     * @param request the reported outcome
     */
    @Transactional
    public void submitResult(final String assignmentId, final ResultRequest request) {
        var attempt = this.require(assignmentId);
        this.rejectIfTerminal(attempt);
        this.settle(attempt, request.status(), request.output(), request.failureReason());
    }

    /**
     * Completes one inference turn on behalf of an assignment and logs it to the transcript --
     * this, not a direct model call, is the only path a harness has to any LLM.
     *
     * @param assignmentId the calling harness's assignment id
     * @param messages the running conversation so far
     * @return the model's response: either a final answer, or a request to call tools
     */
    @Transactional
    public InferenceTurn recordInferenceTurn(
            final String assignmentId, final List<ConversationMessage> messages) {
        var attempt = this.require(assignmentId);
        this.rejectIfTerminal(attempt);

        var turn = this.inferenceGateway.complete(messages);
        this.recordTranscript(assignmentId, attempt, messages, turn);
        return turn;
    }

    /**
     * Executes one tool call a model requested during a prior inference turn, on behalf of an
     * assignment, and logs it -- durably and immediately, before returning the result -- to the
     * tool-call audit trail. Goes through the same {@link McpToolCallbackRegistry} lookup an MCP
     * client would, so the call is logged identically regardless of caller.
     *
     * <p>The audit row is written even when {@code toolName} matches nothing (a model
     * hallucinating a tool it was never offered): the attempt is still recorded, with the error
     * as its result, before the {@link UnknownToolException} is thrown. Writing it here rather
     * than only ever having it show up embedded in a following {@code recordInferenceTurn}'s
     * transcript means the call and its result survive even if the harness never makes another
     * inference call at all -- e.g. it crashes or is reaped immediately after this call.</p>
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
        var attempt = this.require(assignmentId);
        this.rejectIfTerminal(attempt);

        var toolCallback = this.toolCallbackRegistry.findByName(toolName);
        var resultJson = toolCallback.isPresent()
            ? toolCallback.get().call(argumentsJson)
            : this.toJson(Map.of("error", unknownToolMessage(toolName)));
        this.recordToolCall(attempt, toolCallId, toolName, argumentsJson, resultJson);

        if (toolCallback.isEmpty()) {
            throw new UnknownToolException(unknownToolMessage(toolName));
        }
        return new ToolCallInvocationResult(toolCallId, resultJson);
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

    private void recordTranscript(
            final String assignmentId, final TaskAttemptEntity attempt,
            final List<ConversationMessage> messages, final InferenceTurn turn) {
        var transcript = new TaskAttemptTranscriptEntity();
        transcript.setTaskAttempt(attempt);
        transcript.setTurnIndex((int) this.transcriptRepository.countByTaskAttemptId(assignmentId));
        transcript.setPrompt(this.toJson(messages));
        transcript.setResponse(this.responseTextFor(turn));
        transcript.setTokensSpent(turn.tokensSpent());
        this.transcriptRepository.save(transcript);
    }

    private String responseTextFor(final InferenceTurn turn) {
        return turn.content() == null ? this.toJson(turn.toolCalls()) : turn.content();
    }

    private String toJson(final Object value) {
        String result;
        try {
            result = this.objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize transcript content", e);
        }
        return result;
    }

    private void settle(
        final TaskAttemptEntity attempt,
        final TaskAttemptStatus status,
        final String output,
        final String failureReason) {

        attempt.setStatus(status);
        attempt.setResult(output);
        attempt.setFailureReason(failureReason);
        attempt.setCompletedAt(OffsetDateTime.now());
        this.taskAttemptRepository.save(attempt);

        var workflowId = attempt.getTask().getTaskGraph().getTaskPlan().getWorkflow().getId();
        this.eventPublisher.publishEvent(new TaskAttemptSettledEvent(workflowId, attempt.getId()));
    }

    private void rejectIfTerminal(final TaskAttemptEntity attempt) {
        if (isTerminal(attempt.getStatus())) {
            throw new AssignmentAlreadyTerminalException(
                "Assignment " + attempt.getId()
                    + " already reached a terminal status: " + attempt.getStatus());
        }
    }

    private static boolean isTerminal(final TaskAttemptStatus status) {
        return status == TaskAttemptStatus.SUCCEEDED
            || status == TaskAttemptStatus.FAILED
            || status == TaskAttemptStatus.TIMED_OUT
            || status == TaskAttemptStatus.STALLED;
    }

    private TaskAttemptEntity require(final String assignmentId) {
        return this.taskAttemptRepository.findById(assignmentId)
            .orElseThrow(() -> new UnknownAssignmentException(
                "Unknown assignment: " + assignmentId));
    }
}
