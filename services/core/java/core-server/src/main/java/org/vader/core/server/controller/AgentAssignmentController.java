package org.vader.core.server.controller;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.vader.core.server.exceptions.AssignmentAlreadyTerminalException;
import org.vader.core.server.exceptions.OrchestratorUnavailableException;
import org.vader.core.server.exceptions.UnknownAssignmentException;
import org.vader.core.server.exceptions.UnknownToolException;
import org.vader.core.server.models.AssignmentResponse;
import org.vader.core.server.models.HeartbeatRequest;
import org.vader.core.server.models.InferenceRequest;
import org.vader.core.server.models.InferenceTurn;
import org.vader.core.server.models.ResultRequest;
import org.vader.core.server.models.ToolCallInvocationRequest;
import org.vader.core.server.models.ToolCallInvocationResult;
import org.vader.core.server.service.agent.TaskAttemptService;

/**
 * The agent control plane and inference gateway: everything a harness Job calls, over the
 * network, to fetch its work order, report progress, complete a model turn, and report its
 * terminal outcome. This -- not a direct model call -- is the only path a harness has to any LLM,
 * and the only place its status can be written back.
 *
 * <p>The path segment {@code assignmentId} doubles as the caller's bearer credential: it is a
 * single-use, unguessable id minted by {@code TaskGraphScheduler} and known only to the one
 * harness Job it was dispatched to.</p>
 */
@RestController
@RequestMapping("/vader/core-server/agent")
public class AgentAssignmentController {

    private static final Logger logger = LoggerFactory.getLogger(AgentAssignmentController.class);

    @Autowired
    private TaskAttemptService taskAttemptService;

    /**
     * Fetches the work order for an assignment.
     *
     * @param assignmentId the calling harness's assignment id
     * @return the work order
     */
    @GetMapping("/assignments/{assignmentId}")
    public ResponseEntity<AssignmentResponse> fetchAssignment(
        @PathVariable final String assignmentId) {
        return ResponseEntity.ok(this.taskAttemptService.fetchAssignment(assignmentId));
    }

    /**
     * Records a liveness/progress report.
     *
     * @param assignmentId the calling harness's assignment id
     * @param request the reported progress
     * @return an empty 204 response
     */
    @PostMapping("/assignments/{assignmentId}/heartbeat")
    public ResponseEntity<Void> heartbeat(
        @PathVariable final String assignmentId,
        @RequestBody final HeartbeatRequest request) {
        this.taskAttemptService.recordHeartbeat(assignmentId, request);
        return ResponseEntity.noContent().build();
    }

    /**
     * Records an attempt's terminal outcome.
     *
     * @param assignmentId the calling harness's assignment id
     * @param request the reported outcome
     * @return an empty 204 response
     */
    @PostMapping("/assignments/{assignmentId}/result")
    public ResponseEntity<Void> submitResult(
        @PathVariable final String assignmentId,
        @RequestBody final ResultRequest request) {
        this.taskAttemptService.submitResult(assignmentId, request);
        return ResponseEntity.noContent().build();
    }

    /**
     * Completes one inference turn. The only path a harness has to any LLM.
     *
     * @param request the assignment id and running conversation for this turn
     * @return the model's response: either a final answer, or a request to call tools
     */
    @PostMapping("/inference")
    public ResponseEntity<InferenceTurn> inference(@RequestBody final InferenceRequest request) {
        var turn = this.taskAttemptService.recordInferenceTurn(
            request.assignmentId(), request.messages());
        return ResponseEntity.ok(turn);
    }

    /**
     * Executes one tool call a model requested during a prior inference turn.
     *
     * @param request the assignment id and tool call to execute
     * @return the tool's raw result
     */
    @PostMapping("/tool-calls")
    public ResponseEntity<ToolCallInvocationResult> invokeTool(
        @RequestBody final ToolCallInvocationRequest request) {
        var result = this.taskAttemptService.invokeTool(
            request.assignmentId(), request.toolCallId(), request.toolName(),
            request.argumentsJson());
        return ResponseEntity.ok(result);
    }

    /**
     * Maps an unknown assignment id to a 404.
     *
     * @param exception the lookup failure
     * @return a 404 response
     */
    @ExceptionHandler(UnknownAssignmentException.class)
    public ResponseEntity<Map<String, String>> handleUnknownAssignment(
        final UnknownAssignmentException exception) {
        logger.warn("Rejected agent call: {}", exception.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(Map.of("error", "unknown_assignment", "message", exception.getMessage()));
    }

    /**
     * Maps a call against an already-settled assignment to a 409, so a stale or duplicate report
     * from a zombie harness cannot overwrite a newer outcome.
     *
     * @param exception the conflict
     * @return a 409 response
     */
    @ExceptionHandler(AssignmentAlreadyTerminalException.class)
    public ResponseEntity<Map<String, String>> handleAlreadyTerminal(
        final AssignmentAlreadyTerminalException exception) {
        logger.warn("Rejected agent call: {}", exception.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(Map.of(
                "error", "assignment_already_terminal", "message", exception.getMessage()));
    }

    /**
     * Maps an unreachable local LLM to a 502, since the failure originates upstream of this
     * service rather than in the caller's request.
     *
     * @param exception the upstream failure
     * @return a 502 response
     */
    @ExceptionHandler(OrchestratorUnavailableException.class)
    public ResponseEntity<Map<String, String>> handleInferenceUnavailable(
        final OrchestratorUnavailableException exception) {
        logger.warn("Inference gateway unavailable: {}", exception.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
            .body(Map.of("error", "inference_unavailable", "message", exception.getMessage()));
    }

    /**
     * Maps a tool-call request naming a tool that isn't registered to a 400 -- most likely a
     * model hallucinating a tool it was never actually offered.
     *
     * @param exception the lookup failure
     * @return a 400 response
     */
    @ExceptionHandler(UnknownToolException.class)
    public ResponseEntity<Map<String, String>> handleUnknownTool(
        final UnknownToolException exception) {
        logger.warn("Rejected agent tool call: {}", exception.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(Map.of("error", "unknown_tool", "message", exception.getMessage()));
    }
}
