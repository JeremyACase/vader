package org.vader.core.server.service.tools.task;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.vader.common.model.vader.entity.TaskUpdateAuthor;
import org.vader.common.model.vader.entity.TaskUpdateType;
import org.vader.core.server.repository.TaskAttemptRepository;
import org.vader.core.server.repository.TaskRepository;
import org.vader.core.server.service.agent.TaskUpdateService;

/**
 * Exposes {@link TaskUpdateService} to the task-execution agent as an MCP tool, so it can leave
 * an interim progress note against the task it is working on.
 *
 * <p>This tool only ever records {@link TaskUpdateType#UPDATE} -- never a pass/fail/timeout
 * verdict. Those stay reserved for an independent evaluator (and, for timeouts,
 * {@code TaskAttemptReviewService}); a harness grading its own work would defeat the entire point
 * of evaluating it independently. Neither the {@code taskId} nor the {@code taskAttemptId} this
 * method receives is trusted: see {@code TaskAgentService#invokeTool} for why both are always
 * overwritten with the calling assignment's own task and attempt before this method ever runs,
 * per {@code AgentToolAudience.TASK_EXECUTION} never reaching outside its own task.</p>
 */
@Component
@ConditionalOnProperty(
    prefix = "vader.mcp.task-update",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class TaskUpdateTools {

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private TaskAttemptRepository taskAttemptRepository;

    @Autowired
    private TaskUpdateService taskUpdateService;

    /**
     * Records an interim progress note against the calling task's current attempt.
     *
     * @param taskId your own task id, as given in your assignment
     * @param taskAttemptId your own assignment id, as given in your assignment
     * @param description text describing the update
     * @return a short confirmation message
     */
    @Tool(
        name = "post_task_update",
        description = "Record a short interim progress note against the task you are currently "
            + "working on -- what you're doing, or an obstacle you hit. This is not a pass/fail "
            + "verdict: the task's eventual outcome is judged independently once you submit your "
            + "final result.")
    public String postTaskUpdate(
        @ToolParam(description = "Your own task id, as given in your assignment.")
        final String taskId,
        @ToolParam(description = "Your own assignment id, as given in your assignment.")
        final String taskAttemptId,
        @ToolParam(description = "Text describing this update.")
        final String description) {
        var task = this.taskRepository.findById(taskId).orElseThrow();
        var attempt = this.taskAttemptRepository.findById(taskAttemptId).orElseThrow();
        this.taskUpdateService.record(
            task, attempt, TaskUpdateType.UPDATE, description, TaskUpdateAuthor.TASK_AGENT);
        return "Recorded update against task " + task.getId() + ".";
    }
}
