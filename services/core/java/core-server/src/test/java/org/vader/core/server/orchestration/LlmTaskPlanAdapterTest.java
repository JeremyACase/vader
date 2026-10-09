package org.vader.core.server.orchestration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.vader.common.model.vader.dto.ClientPrompt;
import org.vader.common.model.vader.dto.TaskPlan;
import org.vader.core.server.llm.LlmRequestQueue;
import org.vader.core.server.llm.OrchestratorUnavailableException;
import org.vader.core.server.orchestration.model.DecompositionRequest;
import org.vader.core.server.orchestration.model.LlmTaskPlan;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class LlmTaskPlanAdapterTest {

    private static final LlmTaskPlan VALID_PLAN = new LlmTaskPlan(
        "reasoning", "ship it",
        List.of(
            new LlmTaskPlan.LlmTask("design", "draw it", List.of()),
            new LlmTaskPlan.LlmTask("build", "code it", List.of("design"))));

    private final ObjectMapper objectMapper =
        JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();

    private LlmTaskPlanAdapter adapter(final LlmRequestQueue requestQueue) {

        var adapter = new LlmTaskPlanAdapter();
        ReflectionTestUtils.setField(adapter, "requestQueue", requestQueue);
        ReflectionTestUtils.setField(adapter, "objectMapper", this.objectMapper);
        return adapter;
    }

    private static ClientPrompt promptOf(final String text) {
        var prompt = new ClientPrompt();
        prompt.setText(text);
        return prompt;
    }

    @Test
    void decompose_sendsPromptAndReturnsSchemaValidTaskPlanJson() throws Exception {
        var requestQueue = mock(LlmRequestQueue.class);
        when(requestQueue.submit(
            DecompositionLlmExecutor.class,
            new DecompositionRequest("Ship onboarding", List.of(), null)))
            .thenReturn(VALID_PLAN);

        var result = this.adapter(requestQueue)
            .decompose(promptOf("Ship onboarding"), List.of(), null);

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
    void decompose_whenModelReturnsNoTasks_throwsOrchestratorResponse() {
        var requestQueue = mock(LlmRequestQueue.class);
        var emptyPlan = new LlmTaskPlan("reasoning", "ship it", List.of());
        when(requestQueue.submit(
            DecompositionLlmExecutor.class,
            new DecompositionRequest("plan a thing", List.of(), null)))
            .thenReturn(emptyPlan);

        assertThatThrownBy(
            () -> this.adapter(requestQueue).decompose(promptOf("plan a thing"), List.of(), null))
            .isInstanceOf(OrchestratorResponseException.class)
            .hasMessageContaining("usable task plan");
    }

    @Test
    void decompose_whenTaskDependsOnLaterTask_throwsOrchestratorResponse() {
        var requestQueue = mock(LlmRequestQueue.class);
        var forwardReferencingPlan = new LlmTaskPlan("reasoning", "ship it", List.of(
            new LlmTaskPlan.LlmTask("design", "draw it", List.of("build")),
            new LlmTaskPlan.LlmTask("build", "code it", List.of())));
        when(requestQueue.submit(
            DecompositionLlmExecutor.class,
            new DecompositionRequest("plan a thing", List.of(), null)))
            .thenReturn(forwardReferencingPlan);

        assertThatThrownBy(
            () -> this.adapter(requestQueue).decompose(promptOf("plan a thing"), List.of(), null))
            .isInstanceOf(OrchestratorResponseException.class)
            .hasMessageContaining("invalid dependency");
    }

    @Test
    void decompose_whenTaskDependsOnItself_throwsOrchestratorResponse() {
        var requestQueue = mock(LlmRequestQueue.class);
        var selfReferencingPlan = new LlmTaskPlan("reasoning", "ship it", List.of(
            new LlmTaskPlan.LlmTask("design", "draw it", List.of("design"))));
        when(requestQueue.submit(
            DecompositionLlmExecutor.class,
            new DecompositionRequest("plan a thing", List.of(), null)))
            .thenReturn(selfReferencingPlan);

        assertThatThrownBy(
            () -> this.adapter(requestQueue).decompose(promptOf("plan a thing"), List.of(), null))
            .isInstanceOf(OrchestratorResponseException.class)
            .hasMessageContaining("invalid dependency");
    }

    @Test
    void decompose_whenTaskDependsOnAnUnknownTitle_throwsOrchestratorResponse() {
        var requestQueue = mock(LlmRequestQueue.class);
        var unknownTitlePlan = new LlmTaskPlan("reasoning", "ship it", List.of(
            new LlmTaskPlan.LlmTask("design", "draw it", List.of()),
            new LlmTaskPlan.LlmTask("build", "code it", List.of("gather requirements"))));
        when(requestQueue.submit(
            DecompositionLlmExecutor.class,
            new DecompositionRequest("plan a thing", List.of(), null)))
            .thenReturn(unknownTitlePlan);

        assertThatThrownBy(
            () -> this.adapter(requestQueue).decompose(promptOf("plan a thing"), List.of(), null))
            .isInstanceOf(OrchestratorResponseException.class)
            .hasMessageContaining("invalid dependency");
    }

    @Test
    void decompose_toleratesCaseQuotesAndSpacingWhenMatchingTitles() throws Exception {
        var requestQueue = mock(LlmRequestQueue.class);
        var looselyReferencedPlan = new LlmTaskPlan("reasoning", "analyze it", List.of(
            new LlmTaskPlan.LlmTask("Read Spreadsheet", "open it", List.of()),
            new LlmTaskPlan.LlmTask("Identify Key Data", "find patterns",
                List.of("  \"read  spreadsheet\" "))));
        when(requestQueue.submit(
            DecompositionLlmExecutor.class,
            new DecompositionRequest("plan a thing", List.of(), null)))
            .thenReturn(looselyReferencedPlan);

        var plan = this.objectMapper.readValue(
            this.adapter(requestQueue).decompose(promptOf("plan a thing"), List.of(), null),
            TaskPlan.class);

        var read = plan.getTaskGraph().getTasks().get(0);
        var identify = plan.getTaskGraph().getTasks().get(1);
        assertThat(identify.getDependsOnTaskIds()).containsExactly(read.getId());
    }

    @Test
    void decompose_whenTitleIsListedTwice_dependsOnItOnce() throws Exception {
        var requestQueue = mock(LlmRequestQueue.class);
        var duplicatedReferencePlan = new LlmTaskPlan("reasoning", "ship it", List.of(
            new LlmTaskPlan.LlmTask("design", "draw it", List.of()),
            new LlmTaskPlan.LlmTask("build", "code it", List.of("design", "Design"))));
        when(requestQueue.submit(
            DecompositionLlmExecutor.class,
            new DecompositionRequest("plan a thing", List.of(), null)))
            .thenReturn(duplicatedReferencePlan);

        var plan = this.objectMapper.readValue(
            this.adapter(requestQueue).decompose(promptOf("plan a thing"), List.of(), null),
            TaskPlan.class);

        assertThat(plan.getTaskGraph().getTasks().get(1).getDependsOnTaskIds()).hasSize(1);
    }

    @Test
    void decompose_whenModelUnreachable_propagatesOrchestratorUnavailable() {
        var requestQueue = mock(LlmRequestQueue.class);
        when(requestQueue.submit(
            DecompositionLlmExecutor.class,
            new DecompositionRequest("plan a thing", List.of(), null)))
            .thenThrow(new OrchestratorUnavailableException(
                "Could not reach the local LLM.", new IllegalStateException("connection refused")));

        assertThatThrownBy(
            () -> this.adapter(requestQueue).decompose(promptOf("plan a thing"), List.of(), null))
            .isInstanceOf(OrchestratorUnavailableException.class)
            .hasMessageContaining("local LLM");
    }

    @Test
    void decompose_whenTheQueueThrows_throwsOrchestratorResponse() {
        var requestQueue = mock(LlmRequestQueue.class);
        when(requestQueue.submit(
            DecompositionLlmExecutor.class,
            new DecompositionRequest("plan a thing", List.of(), null)))
            .thenThrow(new RuntimeException("boom"));

        assertThatThrownBy(
            () -> this.adapter(requestQueue).decompose(promptOf("plan a thing"), List.of(), null))
            .isInstanceOf(OrchestratorResponseException.class);
    }
}
