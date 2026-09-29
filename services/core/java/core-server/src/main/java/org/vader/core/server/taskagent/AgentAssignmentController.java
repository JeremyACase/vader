package org.vader.core.server.taskagent;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.vader.core.server.taskagent.model.AssignmentResponse;
import org.vader.core.server.taskagent.model.HeartbeatRequest;
import org.vader.core.server.taskagent.model.InferenceRequest;
import org.vader.core.server.taskagent.model.InferenceTurn;
import org.vader.core.server.taskagent.model.ResultRequest;
import org.vader.core.server.taskagent.model.ToolCallInvocationRequest;
import org.vader.core.server.taskagent.model.ToolCallInvocationResult;

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

    @Autowired
    private TaskAttemptLifecycleService lifecycleService;

    @Autowired
    private InferenceTranscriptService inferenceTranscriptService;

    @Autowired
    private TaskToolInvocationService toolInvocationService;

    /**
     * Fetches the work order for an assignment.
     *
     * @param assignmentId the calling harness's assignment id
     * @return the work order
     */
    @GetMapping("/assignments/{assignmentId}")
    public ResponseEntity<AssignmentResponse> fetchAssignment(
        @PathVariable final String assignmentId) {
        return ResponseEntity.ok(this.lifecycleService.fetchAssignment(assignmentId));
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
        this.lifecycleService.recordHeartbeat(assignmentId, request);
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
        this.lifecycleService.submitResult(assignmentId, request);
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
        var turn = this.inferenceTranscriptService.recordInferenceTurn(
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
        var result = this.toolInvocationService.invokeTool(
            request.assignmentId(), request.toolCallId(), request.toolName(),
            request.argumentsJson());
        return ResponseEntity.ok(result);
    }
}
