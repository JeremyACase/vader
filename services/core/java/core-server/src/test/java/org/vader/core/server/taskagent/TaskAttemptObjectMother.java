package org.vader.core.server.taskagent;

import org.vader.common.model.vader.entity.ClientPromptEntity;
import org.vader.common.model.vader.entity.TaskAttemptEntity;
import org.vader.common.model.vader.entity.TaskAttemptStatus;
import org.vader.common.model.vader.entity.TaskEntity;
import org.vader.common.model.vader.entity.TaskGraphEntity;
import org.vader.common.model.vader.entity.TaskPlanEntity;
import org.vader.common.model.vader.entity.WorkflowEntity;

/**
 * Object Mother for the task-agent tests: a running attempt wired all the way up to its workflow
 * and client prompt, the minimum graph every task-agent service walks.
 */
final class TaskAttemptObjectMother {

    static final String ATTEMPT_ID = "aaaaaaaa-1111-2222-3333-444444444444";

    private TaskAttemptObjectMother() {
    }

    static TaskAttemptEntity attemptInWorkflow(final String workflowId) {
        var clientPrompt = new ClientPromptEntity();
        clientPrompt.setText("Plan a birthday party.");
        var workflow = new WorkflowEntity();
        workflow.setId(workflowId);
        workflow.setClientPrompt(clientPrompt);
        var taskPlan = new TaskPlanEntity();
        taskPlan.setWorkflow(workflow);
        var taskGraph = new TaskGraphEntity();
        taskGraph.setTaskPlan(taskPlan);
        var task = new TaskEntity();
        task.setTaskGraph(taskGraph);

        var attempt = new TaskAttemptEntity();
        attempt.setId(ATTEMPT_ID);
        attempt.setTask(task);
        attempt.setStatus(TaskAttemptStatus.RUNNING);
        return attempt;
    }
}
