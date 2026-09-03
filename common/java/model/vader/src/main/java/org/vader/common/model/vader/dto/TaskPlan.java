package org.vader.common.model.vader.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * DTO representing the orchestrator LLM's response: a problem decomposed into actionable tasks.
 *
 * <p>Validated via schema (bean validation) so a malformed orchestrator response is rejected
 * before it's persisted or acted on.
 */
public class TaskPlan extends AbstractModel {

    @NotNull
    private String objective;

    @NotNull
    @Valid
    private TaskGraph taskGraph;

    @Override
    public String getModelType() {
        return "TaskPlan";
    }

    public String getObjective() {
        return this.objective;
    }

    public void setObjective(String objective) {
        this.objective = objective;
    }

    public TaskGraph getTaskGraph() {
        return this.taskGraph;
    }

    public void setTaskGraph(TaskGraph taskGraph) {
        this.taskGraph = taskGraph;
    }
}
