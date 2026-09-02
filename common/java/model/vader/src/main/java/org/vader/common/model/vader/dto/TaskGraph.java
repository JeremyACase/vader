package org.vader.common.model.vader.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.ArrayList;
import java.util.List;

/** DTO representing the directed-acyclic-graph of tasks built from a {@link TaskPlan}. */
public class TaskGraph extends AbstractModel {

    @NotEmpty
    private List<@Valid Task> tasks = new ArrayList<>();

    @Override
    public String getModelType() {
        return "TaskGraph";
    }

    public List<Task> getTasks() {
        return this.tasks;
    }

    public void setTasks(List<Task> tasks) {
        this.tasks = tasks;
    }
}
