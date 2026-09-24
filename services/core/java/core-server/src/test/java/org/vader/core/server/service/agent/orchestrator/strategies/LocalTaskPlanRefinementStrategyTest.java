package org.vader.core.server.service.agent.orchestrator.strategies;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.vader.common.model.vader.dto.TaskGraph;
import org.vader.common.model.vader.dto.TaskPlan;
import org.vader.core.server.exceptions.OrchestratorUnavailableException;
import org.vader.core.server.models.TaskPlanRefinementRequest;
import org.vader.core.server.models.TaskPlanRefinementVerdict;
import org.vader.core.server.service.llm.LlmRequestQueue;
import org.vader.core.server.service.llm.TaskPlanRefinementOutcome;

class LocalTaskPlanRefinementStrategyTest {

    private static final TaskPlanRefinementRequest REQUEST;

    static {
        var taskPlan = new TaskPlan();
        taskPlan.setObjective("ship it");
        taskPlan.setTaskGraph(new TaskGraph());
        REQUEST = new TaskPlanRefinementRequest("do the thing", taskPlan);
    }

    private LocalTaskPlanRefinementStrategy strategy(final LlmRequestQueue requestQueue) {
        var strategy = new LocalTaskPlanRefinementStrategy();
        ReflectionTestUtils.setField(strategy, "requestQueue", requestQueue);
        return strategy;
    }

    @Test
    void critique_returnsTheQueuedVerdict() {
        var requestQueue = mock(LlmRequestQueue.class);
        var verdict = new TaskPlanRefinementVerdict(true, "missing a dependency");
        when(requestQueue.submitTaskPlanRefinement(REQUEST))
            .thenReturn(new TaskPlanRefinementOutcome(verdict, null));

        var result = this.strategy(requestQueue).critique(REQUEST);

        assertThat(result).isSameAs(verdict);
    }

    @Test
    void critique_whenUnreachable_throwsOrchestratorUnavailable() {
        var requestQueue = mock(LlmRequestQueue.class);
        when(requestQueue.submitTaskPlanRefinement(REQUEST))
            .thenReturn(new TaskPlanRefinementOutcome(null, "connection refused"));

        assertThatThrownBy(() -> this.strategy(requestQueue).critique(REQUEST))
            .isInstanceOf(OrchestratorUnavailableException.class)
            .hasMessageContaining("local LLM");
    }
}
