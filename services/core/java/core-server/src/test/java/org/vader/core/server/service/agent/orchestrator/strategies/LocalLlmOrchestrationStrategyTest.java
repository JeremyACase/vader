package org.vader.core.server.service.agent.orchestrator.strategies;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.vader.common.model.vader.dto.ClientPrompt;
import org.vader.common.model.vader.dto.TaskPlan;
import org.vader.core.server.exceptions.OrchestratorResponseException;
import org.vader.core.server.exceptions.OrchestratorUnavailableException;
import org.vader.core.server.service.llm.DecompositionOutcome;
import org.vader.core.server.service.llm.LlmRequestQueue;

class LocalLlmOrchestrationStrategyTest {

    private static final LlmTaskPlan VALID_PLAN = new LlmTaskPlan(
        "reasoning", "ship it",
        List.of(
            new LlmTaskPlan.LlmTask("design", "draw it", List.of()),
            new LlmTaskPlan.LlmTask("build", "code it", List.of(0))));

    private final ObjectMapper objectMapper =
        new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private LocalLlmOrchestrationStrategy strategy(
        final LlmRequestQueue requestQueue, final boolean fallbackToStatic) {

        var strategy = new LocalLlmOrchestrationStrategy();
        ReflectionTestUtils.setField(strategy, "requestQueue", requestQueue);
        ReflectionTestUtils.setField(strategy, "objectMapper", this.objectMapper);
        ReflectionTestUtils.setField(strategy, "fallbackToStatic", fallbackToStatic);
        return strategy;
    }

    private static ClientPrompt promptOf(final String text) {
        var prompt = new ClientPrompt();
        prompt.setText(text);
        return prompt;
    }

    @Test
    void orchestrate_sendsPromptAndReturnsSchemaValidTaskPlanJson() throws Exception {
        var requestQueue = mock(LlmRequestQueue.class);
        when(requestQueue.submitDecomposition("Ship onboarding"))
            .thenReturn(new DecompositionOutcome(VALID_PLAN, null));

        var result = this.strategy(requestQueue, true).orchestrate(promptOf("Ship onboarding"));

        var plan = this.objectMapper.readValue(result, TaskPlan.class);
        assertThat(plan.getObjective()).isEqualTo("ship it");
        assertThat(plan.getTaskGraph().getTasks()).hasSize(2);
        var design = plan.getTaskGraph().getTasks().get(0);
        var build = plan.getTaskGraph().getTasks().get(1);
        assertThat(design.getTitle()).isEqualTo("design");
        assertThat(build.getTitle()).isEqualTo("build");
        // The lean LlmTaskPlan shape means the model is never asked to invent an id/timestamps,
        // so the built TaskPlan leaves them null and passes bean validation downstream --
        // individual tasks still get real ids, though, since dependsOnTaskIds needs something
        // to reference.
        assertThat(plan.getId()).isNull();
        assertThat(design.getId()).isNotBlank();
        assertThat(build.getId()).isNotBlank();
        assertThat(design.getDependsOnTaskIds()).isEmpty();
        assertThat(build.getDependsOnTaskIds()).containsExactly(design.getId());
    }

    @Test
    void orchestrate_whenModelReturnsNoTasks_throwsOrchestratorResponse() {
        var requestQueue = mock(LlmRequestQueue.class);
        var emptyPlan = new LlmTaskPlan("reasoning", "ship it", List.of());
        when(requestQueue.submitDecomposition("plan a thing"))
            .thenReturn(new DecompositionOutcome(emptyPlan, null));

        assertThatThrownBy(
            () -> this.strategy(requestQueue, true).orchestrate(promptOf("plan a thing")))
            .isInstanceOf(OrchestratorResponseException.class)
            .hasMessageContaining("usable task plan");
    }

    @Test
    void orchestrate_whenTaskDependsOnLaterTask_throwsOrchestratorResponse() {
        var requestQueue = mock(LlmRequestQueue.class);
        var forwardReferencingPlan = new LlmTaskPlan("reasoning", "ship it", List.of(
            new LlmTaskPlan.LlmTask("design", "draw it", List.of(1)),
            new LlmTaskPlan.LlmTask("build", "code it", List.of())));
        when(requestQueue.submitDecomposition("plan a thing"))
            .thenReturn(new DecompositionOutcome(forwardReferencingPlan, null));

        assertThatThrownBy(
            () -> this.strategy(requestQueue, true).orchestrate(promptOf("plan a thing")))
            .isInstanceOf(OrchestratorResponseException.class)
            .hasMessageContaining("invalid dependency");
    }

    @Test
    void orchestrate_whenTaskDependsOnItself_throwsOrchestratorResponse() {
        var requestQueue = mock(LlmRequestQueue.class);
        var selfReferencingPlan = new LlmTaskPlan("reasoning", "ship it", List.of(
            new LlmTaskPlan.LlmTask("design", "draw it", List.of(0))));
        when(requestQueue.submitDecomposition("plan a thing"))
            .thenReturn(new DecompositionOutcome(selfReferencingPlan, null));

        assertThatThrownBy(
            () -> this.strategy(requestQueue, true).orchestrate(promptOf("plan a thing")))
            .isInstanceOf(OrchestratorResponseException.class)
            .hasMessageContaining("invalid dependency");
    }

    @Test
    void orchestrate_whenTaskDependsOnOutOfRangeIndex_throwsOrchestratorResponse() {
        var requestQueue = mock(LlmRequestQueue.class);
        var outOfRangePlan = new LlmTaskPlan("reasoning", "ship it", List.of(
            new LlmTaskPlan.LlmTask("design", "draw it", List.of(-1))));
        when(requestQueue.submitDecomposition("plan a thing"))
            .thenReturn(new DecompositionOutcome(outOfRangePlan, null));

        assertThatThrownBy(
            () -> this.strategy(requestQueue, true).orchestrate(promptOf("plan a thing")))
            .isInstanceOf(OrchestratorResponseException.class)
            .hasMessageContaining("invalid dependency");
    }

    @Test
    void orchestrate_whenModelUnreachableAndFallbackEnabled_returnsStaticPlan() throws Exception {
        var requestQueue = mock(LlmRequestQueue.class);
        when(requestQueue.submitDecomposition("plan a thing"))
            .thenReturn(new DecompositionOutcome(null, "connection refused"));

        var result = this.strategy(requestQueue, true).orchestrate(promptOf("plan a thing"));

        var plan = this.objectMapper.readValue(result, TaskPlan.class);
        assertThat(plan.getObjective()).isNotBlank();
        assertThat(plan.getTaskGraph().getTasks()).isNotEmpty();
        assertThat(result).isEqualTo(StaticTaskPlan.JSON);
    }

    @Test
    void orchestrate_whenModelUnreachableAndFallbackDisabled_throwsOrchestratorUnavailable() {
        var requestQueue = mock(LlmRequestQueue.class);
        when(requestQueue.submitDecomposition("plan a thing"))
            .thenReturn(new DecompositionOutcome(null, "connection refused"));

        assertThatThrownBy(
            () -> this.strategy(requestQueue, false).orchestrate(promptOf("plan a thing")))
            .isInstanceOf(OrchestratorUnavailableException.class)
            .hasMessageContaining("local LLM");
    }

    @Test
    void orchestrate_whenTheQueueThrows_throwsOrchestratorResponse() {
        var requestQueue = mock(LlmRequestQueue.class);
        when(requestQueue.submitDecomposition("plan a thing"))
            .thenThrow(new RuntimeException("boom"));

        assertThatThrownBy(
            () -> this.strategy(requestQueue, true).orchestrate(promptOf("plan a thing")))
            .isInstanceOf(OrchestratorResponseException.class);
    }
}
