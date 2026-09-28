package org.vader.core.server.service.agent.orchestrator.strategies;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.vader.common.model.vader.dto.TaskGraph;
import org.vader.common.model.vader.dto.TaskPlan;
import org.vader.core.server.models.TaskPlanRefinementRequest;

class StaticTaskPlanRefinementStrategyTest {

    private final StaticTaskPlanRefinementStrategy strategy =
        new StaticTaskPlanRefinementStrategy();

    @Test
    void critique_alwaysApprovesThePlan() {
        var taskPlan = new TaskPlan();
        taskPlan.setObjective("ship it");
        taskPlan.setTaskGraph(new TaskGraph());
        var request = new TaskPlanRefinementRequest("do the thing", taskPlan);

        var verdict = this.strategy.critique(request);

        assertThat(verdict.needsRevision()).isFalse();
        assertThat(verdict.reasoning()).isNotBlank();
    }
}
