package org.vader.core.server.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.vader.common.library.implementation.service.mapper.ClientPromptDtoMapper;
import org.vader.common.library.implementation.service.mapper.TaskGraphDtoToEntityMapper;
import org.vader.common.library.implementation.service.mapper.TaskPlanDtoToEntityMapper;
import org.vader.common.model.vader.dto.ClientPrompt;
import org.vader.common.model.vader.entity.ClientPromptEntity;
import org.vader.core.server.orchestrator.OrchestratorResponseException;
import org.vader.core.server.orchestrator.interfaces.InterfaceLlmOrchestrationStrategy;
import org.vader.core.server.repository.ClientPromptRepository;
import org.vader.core.server.repository.WorkflowRepository;

class WorkflowServiceTest {

    private static final String VALID_RESPONSE =
        "{\"objective\":\"ship it\",\"taskGraph\":{\"tasks\":"
            + "[{\"title\":\"t\",\"description\":\"d\"}]}}";

    private InterfaceLlmOrchestrationStrategy orchestrator;
    private ClientPromptRepository clientPromptRepository;
    private WorkflowRepository workflowRepository;
    private TaskPlanDtoToEntityMapper taskPlanDtoToEntityMapper;
    private WorkflowService service;

    @BeforeEach
    void setUp() {
        this.orchestrator = mock(InterfaceLlmOrchestrationStrategy.class);
        this.clientPromptRepository = mock(ClientPromptRepository.class);
        this.workflowRepository = mock(WorkflowRepository.class);
        this.taskPlanDtoToEntityMapper = mock(TaskPlanDtoToEntityMapper.class);
        this.service = new WorkflowService(
            this.orchestrator,
            new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false),
            newValidator(),
            new ClientPromptDtoMapper(),
            this.taskPlanDtoToEntityMapper,
            this.clientPromptRepository,
            this.workflowRepository);
    }

    private static Validator newValidator() {
        return Validation.buildDefaultValidatorFactory().getValidator();
    }

    private static ClientPromptEntity prompt() {
        var prompt = new ClientPromptEntity();
        prompt.setText("Decompose this problem.");
        return prompt;
    }

    private void assertRejected(final String orchestratorResponse, final String expectedFragment) {
        when(this.orchestrator.orchestrate(any(ClientPrompt.class)))
            .thenReturn(orchestratorResponse);

        assertThatThrownBy(() -> this.service.decompose(prompt()))
            .isInstanceOf(OrchestratorResponseException.class)
            .hasMessageContaining(expectedFragment);

        verify(this.orchestrator).orchestrate(any(ClientPrompt.class));
        verifyNoInteractions(
            this.clientPromptRepository, this.workflowRepository, this.taskPlanDtoToEntityMapper);
    }

    @Test
    void decompose_whenResponseIsNull_throwsAndPersistsNothing() {
        assertRejected(null, "empty response");
    }

    @Test
    void decompose_whenResponseIsBlank_throwsAndPersistsNothing() {
        assertRejected("   \n  ", "empty response");
    }

    @Test
    void decompose_whenResponseIsNotJson_throwsAndPersistsNothing() {
        assertRejected("Sure! Here is your plan: do the thing.", "could not be parsed");
    }

    @Test
    void decompose_whenResponseIsJsonButWrongShape_throwsAndPersistsNothing() {
        assertRejected("[\"do the thing\"]", "could not be parsed");
    }

    @Test
    void decompose_whenObjectiveIsMissing_throwsSchemaViolationForObjective() {
        assertRejected(
            "{\"taskGraph\":{\"tasks\":[{\"title\":\"t\",\"description\":\"d\"}]}}", "objective");
    }

    @Test
    void decompose_whenTaskGraphIsMissing_throwsSchemaViolationForTaskGraph() {
        assertRejected("{\"objective\":\"ship it\"}", "taskGraph");
    }

    @Test
    void decompose_whenTaskGraphHasNoTasks_throwsSchemaViolationForTasks() {
        assertRejected("{\"objective\":\"ship it\",\"taskGraph\":{\"tasks\":[]}}", "tasks");
    }

    @Test
    void decompose_whenTaskIsMissingItsTitle_throwsSchemaViolationForTitle() {
        assertRejected(
            "{\"objective\":\"ship it\",\"taskGraph\":{\"tasks\":[{\"description\":\"d\"}]}}",
            "title");
    }

    @Test
    void decompose_whenResponseIsSchemaValid_buildsWorkflowLinkedBothWaysAndSavesOnce() {
        var realMapper = new TaskPlanDtoToEntityMapper();
        ReflectionTestUtils.setField(
            realMapper, "taskGraphDtoToEntityMapper", new TaskGraphDtoToEntityMapper());
        var wired = new WorkflowService(
            this.orchestrator,
            new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false),
            newValidator(),
            new ClientPromptDtoMapper(),
            realMapper,
            this.clientPromptRepository,
            this.workflowRepository);

        when(this.orchestrator.orchestrate(any(ClientPrompt.class))).thenReturn(VALID_RESPONSE);
        when(this.clientPromptRepository.save(any())).thenAnswer(call -> call.getArgument(0));
        when(this.workflowRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        var workflow = wired.decompose(prompt());

        assertThat(workflow.getClientPrompt()).isNotNull();
        assertThat(workflow.getTaskPlan()).isNotNull();
        assertThat(workflow.getTaskPlan().getObjective()).isEqualTo("ship it");
        assertThat(workflow.getTaskPlan().getWorkflow()).isSameAs(workflow);
        assertThat(workflow.getTaskPlan().getTaskGraph().getTaskPlan())
            .isSameAs(workflow.getTaskPlan());
        verify(this.workflowRepository).save(any());
    }
}
