package org.vader.core.server.orchestration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.vader.common.library.implementation.service.mapper.ClientPromptDtoMapper;
import org.vader.common.library.implementation.service.mapper.TaskGraphDtoToEntityMapper;
import org.vader.common.library.implementation.service.mapper.TaskPlanDtoToEntityMapper;
import org.vader.common.model.vader.dto.ClientPrompt;
import org.vader.common.model.vader.entity.ClientPromptEntity;
import org.vader.common.model.vader.entity.ObjectMetadataEntity;
import org.vader.common.model.vader.entity.TaskEntity;
import org.vader.common.model.vader.entity.TaskUpdateAuthor;
import org.vader.common.model.vader.entity.TaskUpdateType;
import org.vader.core.server.events.EventPublishingFacade;
import org.vader.core.server.intake.ClientPromptRepository;
import org.vader.core.server.llm.LlmRequestQueue;
import org.vader.core.server.orchestration.model.AttachedFile;
import org.vader.core.server.orchestration.model.TaskPlanRefinementRequest;
import org.vader.core.server.orchestration.model.TaskPlanRefinementVerdict;
import org.vader.core.server.workflow.TaskUpdateService;
import org.vader.core.server.workflow.WorkflowDecomposedEvent;
import org.vader.core.server.workflow.WorkflowRepository;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class OrchestratorAgentServiceTest {

    private static final String PROMPT_ID = "aaaaaaaa-1111-2222-3333-444444444444";

    private static final String VALID_RESPONSE =
        "{\"objective\":\"ship it\",\"taskGraph\":{\"tasks\":"
            + "[{\"title\":\"t\",\"description\":\"d\"}]}}";

    private LlmTaskPlanAdapter taskPlanAdapter;
    private ClientPromptRepository clientPromptRepository;
    private WorkflowRepository workflowRepository;
    private TaskPlanDtoToEntityMapper taskPlanDtoToEntityMapper;
    private EventPublishingFacade eventPublishingFacade;
    private TaskUpdateService taskUpdateService;
    private LlmRequestQueue requestQueue;
    private OrchestratorAgentService service;

    @BeforeEach
    void setUp() {
        this.taskPlanAdapter = mock(LlmTaskPlanAdapter.class);
        this.clientPromptRepository = mock(ClientPromptRepository.class);
        this.workflowRepository = mock(WorkflowRepository.class);
        this.taskPlanDtoToEntityMapper = mock(TaskPlanDtoToEntityMapper.class);
        this.eventPublishingFacade = mock(EventPublishingFacade.class);
        this.taskUpdateService = mock(TaskUpdateService.class);
        this.requestQueue = mock(LlmRequestQueue.class);
        // Default: always approved, so tests that don't care about refinement take the
        // single-decomposition path.
        when(this.requestQueue.submit(eq(TaskPlanRefinementLlmExecutor.class), any()))
            .thenReturn(new TaskPlanRefinementVerdict(false, "looks fine"));
        this.service = buildService(this.taskPlanDtoToEntityMapper);

        var prompt = new ClientPromptEntity();
        prompt.setText("Decompose this problem.");
        when(this.clientPromptRepository.findById(PROMPT_ID)).thenReturn(Optional.of(prompt));
    }

    private OrchestratorAgentService buildService(
            final TaskPlanDtoToEntityMapper taskPlanMapper) {
        var schemaValidator = new TaskPlanSchemaValidator();
        ReflectionTestUtils.setField(schemaValidator, "objectMapper",
            JsonMapper.builder()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build());
        ReflectionTestUtils.setField(schemaValidator, "validator", newValidator());

        var refinementService = new TaskPlanRefinementService();
        ReflectionTestUtils.setField(refinementService, "taskPlanAdapter", this.taskPlanAdapter);
        ReflectionTestUtils.setField(refinementService, "schemaValidator", schemaValidator);
        ReflectionTestUtils.setField(refinementService, "requestQueue", this.requestQueue);
        ReflectionTestUtils.setField(refinementService, "maxTaskPlanRevisions", 1);

        var built = new OrchestratorAgentService();
        ReflectionTestUtils.setField(built, "clientPromptDtoMapper", new ClientPromptDtoMapper());
        ReflectionTestUtils.setField(built, "taskPlanRefinementService", refinementService);
        ReflectionTestUtils.setField(built, "taskPlanDtoToEntityMapper", taskPlanMapper);
        ReflectionTestUtils.setField(
            built, "clientPromptRepository", this.clientPromptRepository);
        ReflectionTestUtils.setField(built, "workflowRepository", this.workflowRepository);
        ReflectionTestUtils.setField(built, "eventPublishingFacade", this.eventPublishingFacade);
        ReflectionTestUtils.setField(built, "taskUpdateService", this.taskUpdateService);
        return built;
    }

    private static Validator newValidator() {
        return Validation.buildDefaultValidatorFactory().getValidator();
    }

    private void assertRejected(final String orchestratorResponse, final String expectedFragment) {
        when(this.taskPlanAdapter.decompose(any(ClientPrompt.class), any(), any()))
            .thenReturn(orchestratorResponse);

        assertThatThrownBy(() -> this.service.decompose(PROMPT_ID))
            .isInstanceOf(OrchestratorResponseException.class)
            .hasMessageContaining(expectedFragment);

        verify(this.taskPlanAdapter).decompose(any(ClientPrompt.class), any(), any());
        verifyNoInteractions(this.workflowRepository, this.taskPlanDtoToEntityMapper);
    }

    @Test
    void decompose_whenResponseIsNull_throwsAndPersistsNoWorkflow() {
        assertRejected(null, "empty response");
    }

    @Test
    void decompose_whenResponseIsBlank_throwsAndPersistsNoWorkflow() {
        assertRejected("   \n  ", "empty response");
    }

    @Test
    void decompose_whenResponseIsNotJson_throwsAndPersistsNoWorkflow() {
        assertRejected("Sure! Here is your plan: do the thing.", "could not be parsed");
    }

    @Test
    void decompose_whenResponseIsJsonButWrongShape_throwsAndPersistsNoWorkflow() {
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
        var wired = buildService(realMapper);

        when(this.taskPlanAdapter.decompose(any(ClientPrompt.class), any(), any()))
            .thenReturn(VALID_RESPONSE);
        when(this.workflowRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        var workflow = wired.decompose(PROMPT_ID);

        assertThat(workflow.getClientPrompt()).isNotNull();
        assertThat(workflow.getTaskPlan()).isNotNull();
        assertThat(workflow.getTaskPlan().getObjective()).isEqualTo("ship it");
        assertThat(workflow.getTaskPlan().getWorkflow()).isSameAs(workflow);
        assertThat(workflow.getTaskPlan().getTaskGraph().getTaskPlan())
            .isSameAs(workflow.getTaskPlan());
        verify(this.workflowRepository).save(any());
        verify(this.eventPublishingFacade).publish(new WorkflowDecomposedEvent(workflow.getId()));
    }

    @Test
    void decompose_passesTheAttachedFilesNamesAndTypesToThePlanner() {
        var file = new ObjectMetadataEntity();
        file.setOriginalFilename("Finances.xlsx");
        file.setContentType("application/vnd.ms-excel");
        this.clientPromptRepository.findById(PROMPT_ID).orElseThrow().getFiles().add(file);
        var realMapper = new TaskPlanDtoToEntityMapper();
        ReflectionTestUtils.setField(
            realMapper, "taskGraphDtoToEntityMapper", new TaskGraphDtoToEntityMapper());
        when(this.taskPlanAdapter.decompose(any(ClientPrompt.class), any(), any()))
            .thenReturn(VALID_RESPONSE);
        when(this.workflowRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        buildService(realMapper).decompose(PROMPT_ID);

        verify(this.taskPlanAdapter).decompose(any(ClientPrompt.class),
            eq(List.of(new AttachedFile("Finances.xlsx", "application/vnd.ms-excel"))), isNull());
    }

    @Test
    void decompose_recordsCreatedUpdateForEveryTaskAuthoredBySystem() {
        var realMapper = new TaskPlanDtoToEntityMapper();
        ReflectionTestUtils.setField(
            realMapper, "taskGraphDtoToEntityMapper", new TaskGraphDtoToEntityMapper());
        var wired = buildService(realMapper);

        when(this.taskPlanAdapter.decompose(any(ClientPrompt.class), any(), any()))
            .thenReturn(VALID_RESPONSE);
        when(this.workflowRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        var workflow = wired.decompose(PROMPT_ID);

        var task = workflow.getTaskPlan().getTaskGraph().getTasks().iterator().next();
        verify(this.taskUpdateService).record(
            same(task), isNull(), eq(TaskUpdateType.CREATED), eq(task.getDescription()),
            eq(TaskUpdateAuthor.SYSTEM));
    }

    @Test
    void decompose_whenFirstPlanHasDanglingDependency_revisesAndUsesTheSecondPlan() {
        var realMapper = new TaskPlanDtoToEntityMapper();
        ReflectionTestUtils.setField(
            realMapper, "taskGraphDtoToEntityMapper", new TaskGraphDtoToEntityMapper());
        var wired = buildService(realMapper);

        var danglingResponse = "{\"objective\":\"ship it\",\"taskGraph\":{\"tasks\":"
            + "[{\"id\":\"11111111-1111-1111-1111-111111111111\",\"title\":\"t\","
            + "\"description\":\"d\",\"dependsOnTaskIds\":[\"missing\"]}]}}";
        when(this.taskPlanAdapter.decompose(any(ClientPrompt.class), any(), any()))
            .thenReturn(danglingResponse, VALID_RESPONSE);
        when(this.workflowRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        var workflow = wired.decompose(PROMPT_ID);

        assertThat(workflow.getTaskPlan().getObjective()).isEqualTo("ship it");
        // The first (dangling) plan never reaches the refinement critique -- its structural
        // problem is already known for free. The second (valid) plan does reach it, and is
        // approved by the default stub in setUp().
        verify(this.requestQueue).submit(
            eq(TaskPlanRefinementLlmExecutor.class), any(TaskPlanRefinementRequest.class));

        // The revision is asked for with the problem as separate guidance -- the user's own
        // request text reaches the model unchanged, never with the critique spliced into it.
        var promptCaptor = ArgumentCaptor.forClass(ClientPrompt.class);
        var guidanceCaptor = ArgumentCaptor.forClass(String.class);
        verify(this.taskPlanAdapter, times(2))
            .decompose(promptCaptor.capture(), any(), guidanceCaptor.capture());
        assertThat(guidanceCaptor.getAllValues().get(0)).isNull();
        assertThat(guidanceCaptor.getAllValues().get(1)).contains("unknown task id 'missing'");
        assertThat(promptCaptor.getAllValues().get(1).getText())
            .isEqualTo(promptCaptor.getAllValues().get(0).getText())
            .doesNotContain("unknown task id 'missing'");
    }

    @Test
    void decompose_whenTheCriticNamesMissingDependencies_addsThemWithoutReplanning() {
        var realMapper = new TaskPlanDtoToEntityMapper();
        ReflectionTestUtils.setField(
            realMapper, "taskGraphDtoToEntityMapper", new TaskGraphDtoToEntityMapper());
        var wired = buildService(realMapper);

        var sequentialPlanWithNoEdges = "{\"objective\":\"analyze it\",\"taskGraph\":{\"tasks\":["
            + "{\"id\":\"11111111-1111-1111-1111-111111111111\",\"title\":\"Read Spreadsheet\","
            + "\"description\":\"open it\"},"
            + "{\"id\":\"22222222-2222-2222-2222-222222222222\",\"title\":\"Identify Key Data\","
            + "\"description\":\"find patterns\"}]}}";
        when(this.taskPlanAdapter.decompose(any(ClientPrompt.class), any(), any()))
            .thenReturn(sequentialPlanWithNoEdges);
        when(this.requestQueue.submit(
            eq(TaskPlanRefinementLlmExecutor.class), any(TaskPlanRefinementRequest.class)))
            .thenReturn(new TaskPlanRefinementVerdict(false, "reading must come first",
                List.of(new TaskPlanRefinementVerdict.MissingDependency(
                    "Identify Key Data", List.of("Read Spreadsheet")))));
        when(this.workflowRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        var workflow = wired.decompose(PROMPT_ID);

        // Applied directly -- the planner is never asked to regenerate the plan to add it.
        verify(this.taskPlanAdapter, times(1)).decompose(any(ClientPrompt.class), any(), any());
        var tasks = workflow.getTaskPlan().getTaskGraph().getTasks();
        var identify = tasks.stream()
            .filter(task -> task.getTitle().equals("Identify Key Data")).findFirst().orElseThrow();
        assertThat(identify.getDependsOn())
            .extracting(TaskEntity::getTitle)
            .containsExactly("Read Spreadsheet");
    }

    @Test
    void decompose_whenRefinementFlagsTheFirstPlan_revisesAndUsesTheSecondPlan() {
        var realMapper = new TaskPlanDtoToEntityMapper();
        ReflectionTestUtils.setField(
            realMapper, "taskGraphDtoToEntityMapper", new TaskGraphDtoToEntityMapper());
        var wired = buildService(realMapper);

        when(this.taskPlanAdapter.decompose(any(ClientPrompt.class), any(), any()))
            .thenReturn(VALID_RESPONSE);
        when(this.requestQueue.submit(
            eq(TaskPlanRefinementLlmExecutor.class), any(TaskPlanRefinementRequest.class)))
            .thenReturn(
                new TaskPlanRefinementVerdict(true, "task doesn't serve the objective"),
                new TaskPlanRefinementVerdict(false, "looks fine now"));
        when(this.workflowRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        var workflow = wired.decompose(PROMPT_ID);

        assertThat(workflow.getTaskPlan().getObjective()).isEqualTo("ship it");
        verify(this.taskPlanAdapter, times(2))
            .decompose(any(ClientPrompt.class), any(), any());
        verify(this.requestQueue, times(2)).submit(
            eq(TaskPlanRefinementLlmExecutor.class), any(TaskPlanRefinementRequest.class));
    }

    @Test
    void decompose_whenRevisionsAreExhausted_proceedsWithTheLastPlanAnyway() {
        var realMapper = new TaskPlanDtoToEntityMapper();
        ReflectionTestUtils.setField(
            realMapper, "taskGraphDtoToEntityMapper", new TaskGraphDtoToEntityMapper());
        var wired = buildService(realMapper);

        // Structurally sound (so it actually persists), but the critique never approves it --
        // the plan the refinement critique keeps flagging is still usable, unlike a dangling
        // dependency, which the entity mapper would refuse to persist no matter how many
        // revisions ran.
        when(this.taskPlanAdapter.decompose(any(ClientPrompt.class), any(), any()))
            .thenReturn(VALID_RESPONSE);
        when(this.requestQueue.submit(
            eq(TaskPlanRefinementLlmExecutor.class), any(TaskPlanRefinementRequest.class)))
            .thenReturn(new TaskPlanRefinementVerdict(true, "still not great"));
        when(this.workflowRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        var workflow = wired.decompose(PROMPT_ID);

        assertThat(workflow.getTaskPlan().getObjective()).isEqualTo("ship it");
        // maxTaskPlanRevisions is 1 (set in buildService): the initial decomposition, plus one
        // revision attempt, both still flagged -- proceeds anyway rather than throwing or
        // looping forever.
        verify(this.taskPlanAdapter, times(2)).decompose(any(ClientPrompt.class), any(), any());
        verify(this.requestQueue, times(2)).submit(
            eq(TaskPlanRefinementLlmExecutor.class), any(TaskPlanRefinementRequest.class));
    }
}
