package org.vader.core.server.service.agent.orchestrator;

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

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import org.vader.common.library.implementation.service.mapper.ClientPromptDtoMapper;
import org.vader.common.library.implementation.service.mapper.TaskGraphDtoToEntityMapper;
import org.vader.common.library.implementation.service.mapper.TaskPlanDtoToEntityMapper;
import org.vader.common.model.vader.dto.ClientPrompt;
import org.vader.common.model.vader.entity.ClientPromptEntity;
import org.vader.common.model.vader.entity.TaskAttemptEntity;
import org.vader.common.model.vader.entity.TaskEntity;
import org.vader.common.model.vader.entity.TaskUpdateAuthor;
import org.vader.common.model.vader.entity.TaskUpdateEntity;
import org.vader.common.model.vader.entity.TaskUpdateType;
import org.vader.core.server.exceptions.OrchestratorResponseException;
import org.vader.core.server.models.ReattemptDecision;
import org.vader.core.server.models.ReattemptDecisionRequest;
import org.vader.core.server.models.TaskPlanRefinementRequest;
import org.vader.core.server.models.TaskPlanRefinementVerdict;
import org.vader.core.server.models.WorkflowDecomposedEvent;
import org.vader.core.server.repository.ClientPromptRepository;
import org.vader.core.server.repository.TaskAttemptRepository;
import org.vader.core.server.repository.TaskUpdateRepository;
import org.vader.core.server.repository.WorkflowRepository;
import org.vader.core.server.service.agent.TaskUpdateService;
import org.vader.core.server.service.agent.orchestrator.strategies.interfaces.InterfaceLlmOrchestrationStrategy;
import org.vader.core.server.service.agent.orchestrator.strategies.interfaces.InterfaceReattemptDecisionStrategy;
import org.vader.core.server.service.agent.orchestrator.strategies.interfaces.InterfaceTaskPlanRefinementStrategy;

class OrchestratorAgentServiceTest {

    private static final String PROMPT_ID = "aaaaaaaa-1111-2222-3333-444444444444";

    private static final String VALID_RESPONSE =
        "{\"objective\":\"ship it\",\"taskGraph\":{\"tasks\":"
            + "[{\"title\":\"t\",\"description\":\"d\"}]}}";

    private static final String ATTEMPT_ID = "bbbbbbbb-1111-2222-3333-444444444444";

    private InterfaceLlmOrchestrationStrategy orchestrator;
    private ClientPromptRepository clientPromptRepository;
    private WorkflowRepository workflowRepository;
    private TaskPlanDtoToEntityMapper taskPlanDtoToEntityMapper;
    private ApplicationEventPublisher eventPublisher;
    private TaskAttemptRepository taskAttemptRepository;
    private TaskUpdateRepository taskUpdateRepository;
    private TaskUpdateService taskUpdateService;
    private InterfaceReattemptDecisionStrategy reattemptDecisionStrategy;
    private TaskGraphScheduler taskGraphScheduler;
    private InterfaceTaskPlanRefinementStrategy taskPlanRefinementStrategy;
    private OrchestratorAgentService service;

    @BeforeEach
    void setUp() {
        this.orchestrator = mock(InterfaceLlmOrchestrationStrategy.class);
        this.clientPromptRepository = mock(ClientPromptRepository.class);
        this.workflowRepository = mock(WorkflowRepository.class);
        this.taskPlanDtoToEntityMapper = mock(TaskPlanDtoToEntityMapper.class);
        this.eventPublisher = mock(ApplicationEventPublisher.class);
        this.taskAttemptRepository = mock(TaskAttemptRepository.class);
        this.taskUpdateRepository = mock(TaskUpdateRepository.class);
        this.taskUpdateService = mock(TaskUpdateService.class);
        this.reattemptDecisionStrategy = mock(InterfaceReattemptDecisionStrategy.class);
        this.taskGraphScheduler = mock(TaskGraphScheduler.class);
        this.taskPlanRefinementStrategy = mock(InterfaceTaskPlanRefinementStrategy.class);
        // Default: always approved, so tests that don't care about refinement (most of them)
        // exercise the exact same single-decomposition path as before this feature existed.
        when(this.taskPlanRefinementStrategy.critique(any()))
            .thenReturn(new TaskPlanRefinementVerdict(false, "looks fine"));
        this.service = buildService(this.taskPlanDtoToEntityMapper);

        var prompt = new ClientPromptEntity();
        prompt.setText("Decompose this problem.");
        when(this.clientPromptRepository.findById(PROMPT_ID)).thenReturn(Optional.of(prompt));
    }

    private OrchestratorAgentService buildService(
            final TaskPlanDtoToEntityMapper taskPlanMapper) {
        var built = new OrchestratorAgentService();
        ReflectionTestUtils.setField(built, "orchestrator", this.orchestrator);
        ReflectionTestUtils.setField(built, "objectMapper",
            new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false));
        ReflectionTestUtils.setField(built, "validator", newValidator());
        ReflectionTestUtils.setField(built, "clientPromptDtoMapper", new ClientPromptDtoMapper());
        ReflectionTestUtils.setField(built, "taskPlanDtoToEntityMapper", taskPlanMapper);
        ReflectionTestUtils.setField(
            built, "clientPromptRepository", this.clientPromptRepository);
        ReflectionTestUtils.setField(built, "workflowRepository", this.workflowRepository);
        ReflectionTestUtils.setField(built, "eventPublisher", this.eventPublisher);
        ReflectionTestUtils.setField(built, "taskAttemptRepository", this.taskAttemptRepository);
        ReflectionTestUtils.setField(built, "taskUpdateRepository", this.taskUpdateRepository);
        ReflectionTestUtils.setField(built, "taskUpdateService", this.taskUpdateService);
        ReflectionTestUtils.setField(
            built, "reattemptDecisionStrategy", this.reattemptDecisionStrategy);
        ReflectionTestUtils.setField(built, "taskGraphScheduler", this.taskGraphScheduler);
        ReflectionTestUtils.setField(
            built, "taskPlanRefinementStrategy", this.taskPlanRefinementStrategy);
        ReflectionTestUtils.setField(built, "maxAttemptsPerTask", 3);
        ReflectionTestUtils.setField(built, "maxTaskPlanRevisions", 1);
        return built;
    }

    private static TaskAttemptEntity attemptOf(final TaskEntity task, final int attemptNumber) {
        var attempt = new TaskAttemptEntity();
        attempt.setId(ATTEMPT_ID);
        attempt.setTask(task);
        attempt.setAttemptNumber(attemptNumber);
        return attempt;
    }

    private static Validator newValidator() {
        return Validation.buildDefaultValidatorFactory().getValidator();
    }

    private void assertRejected(final String orchestratorResponse, final String expectedFragment) {
        when(this.orchestrator.orchestrate(any(ClientPrompt.class), any()))
            .thenReturn(orchestratorResponse);

        assertThatThrownBy(() -> this.service.decompose(PROMPT_ID))
            .isInstanceOf(OrchestratorResponseException.class)
            .hasMessageContaining(expectedFragment);

        verify(this.orchestrator).orchestrate(any(ClientPrompt.class), any());
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

        when(this.orchestrator.orchestrate(any(ClientPrompt.class), any()))
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
        verify(this.eventPublisher).publishEvent(new WorkflowDecomposedEvent(workflow.getId()));
    }

    @Test
    void decompose_recordsCreatedUpdateForEveryTaskAuthoredBySystem() {
        var realMapper = new TaskPlanDtoToEntityMapper();
        ReflectionTestUtils.setField(
            realMapper, "taskGraphDtoToEntityMapper", new TaskGraphDtoToEntityMapper());
        var wired = buildService(realMapper);

        when(this.orchestrator.orchestrate(any(ClientPrompt.class), any()))
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
        when(this.orchestrator.orchestrate(any(ClientPrompt.class), any()))
            .thenReturn(danglingResponse, VALID_RESPONSE);
        when(this.workflowRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        var workflow = wired.decompose(PROMPT_ID);

        assertThat(workflow.getTaskPlan().getObjective()).isEqualTo("ship it");
        // The first (dangling) plan never reaches the refinement strategy -- its structural
        // problem is already known for free. The second (valid) plan does reach it, and is
        // approved by the default stub in setUp().
        verify(this.taskPlanRefinementStrategy).critique(any(TaskPlanRefinementRequest.class));

        // The revision is asked for with the problem as separate guidance -- the user's own
        // request text reaches the model unchanged, never with the critique spliced into it.
        var promptCaptor = ArgumentCaptor.forClass(ClientPrompt.class);
        var guidanceCaptor = ArgumentCaptor.forClass(String.class);
        verify(this.orchestrator, times(2))
            .orchestrate(promptCaptor.capture(), guidanceCaptor.capture());
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
        when(this.orchestrator.orchestrate(any(ClientPrompt.class), any()))
            .thenReturn(sequentialPlanWithNoEdges);
        when(this.taskPlanRefinementStrategy.critique(any(TaskPlanRefinementRequest.class)))
            .thenReturn(new TaskPlanRefinementVerdict(false, "reading must come first",
                List.of(new TaskPlanRefinementVerdict.MissingDependency(
                    "Identify Key Data", List.of("Read Spreadsheet")))));
        when(this.workflowRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        var workflow = wired.decompose(PROMPT_ID);

        // Applied directly -- the planner is never asked to regenerate the plan to add it.
        verify(this.orchestrator, times(1)).orchestrate(any(ClientPrompt.class), any());
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

        when(this.orchestrator.orchestrate(any(ClientPrompt.class), any()))
            .thenReturn(VALID_RESPONSE);
        when(this.taskPlanRefinementStrategy.critique(any(TaskPlanRefinementRequest.class)))
            .thenReturn(
                new TaskPlanRefinementVerdict(true, "task doesn't serve the objective"),
                new TaskPlanRefinementVerdict(false, "looks fine now"));
        when(this.workflowRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        var workflow = wired.decompose(PROMPT_ID);

        assertThat(workflow.getTaskPlan().getObjective()).isEqualTo("ship it");
        verify(this.orchestrator, times(2))
            .orchestrate(any(ClientPrompt.class), any());
        verify(this.taskPlanRefinementStrategy, times(2))
            .critique(any(TaskPlanRefinementRequest.class));
    }

    @Test
    void decompose_whenRevisionsAreExhausted_proceedsWithTheLastPlanAnyway() {
        var realMapper = new TaskPlanDtoToEntityMapper();
        ReflectionTestUtils.setField(
            realMapper, "taskGraphDtoToEntityMapper", new TaskGraphDtoToEntityMapper());
        var wired = buildService(realMapper);

        // Structurally sound (so it actually persists), but the critique never approves it --
        // the plan the refinement strategy keeps flagging is still usable, unlike a dangling
        // dependency, which the entity mapper would refuse to persist no matter how many
        // revisions ran.
        when(this.orchestrator.orchestrate(any(ClientPrompt.class), any()))
            .thenReturn(VALID_RESPONSE);
        when(this.taskPlanRefinementStrategy.critique(any(TaskPlanRefinementRequest.class)))
            .thenReturn(new TaskPlanRefinementVerdict(true, "still not great"));
        when(this.workflowRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        var workflow = wired.decompose(PROMPT_ID);

        assertThat(workflow.getTaskPlan().getObjective()).isEqualTo("ship it");
        // maxTaskPlanRevisions is 1 (set in buildService): the initial decomposition, plus one
        // revision attempt, both still flagged -- proceeds anyway rather than throwing or
        // looping forever.
        verify(this.orchestrator, times(2)).orchestrate(any(ClientPrompt.class), any());
        verify(this.taskPlanRefinementStrategy, times(2))
            .critique(any(TaskPlanRefinementRequest.class));
    }

    @Test
    void decideReattempt_whenAttemptCapReached_givesUpWithoutConsultingTheStrategy() {
        var task = new TaskEntity();
        task.setId("t1");
        var attempt = attemptOf(task, 3);
        when(this.taskAttemptRepository.findById(ATTEMPT_ID)).thenReturn(Optional.of(attempt));

        this.service.decideReattempt(ATTEMPT_ID);

        verifyNoInteractions(this.reattemptDecisionStrategy);
        verify(this.taskGraphScheduler, never()).dispatch(any(), any(Integer.class));
        var descriptionCaptor = ArgumentCaptor.forClass(String.class);
        verify(this.taskUpdateService).record(
            same(task), same(attempt), eq(TaskUpdateType.UPDATE), descriptionCaptor.capture(),
            eq(TaskUpdateAuthor.ORCHESTRATOR));
        assertThat(descriptionCaptor.getValue()).contains("cap");
    }

    @Test
    void decideReattempt_whenStrategyApproves_dispatchesTheNextAttempt() {
        var task = new TaskEntity();
        task.setId("t1");
        task.setTitle("title");
        task.setDescription("description");
        var attempt = attemptOf(task, 1);
        when(this.taskAttemptRepository.findById(ATTEMPT_ID)).thenReturn(Optional.of(attempt));
        when(this.taskUpdateRepository.findFirstByTaskAttemptIdAndTypeInOrderByCreatedAtDesc(
                eq(ATTEMPT_ID), any()))
            .thenReturn(Optional.empty());
        when(this.taskUpdateRepository.findByTaskIdOrderByCreatedAtAsc("t1"))
            .thenReturn(List.of());
        when(this.reattemptDecisionStrategy.decide(any()))
            .thenReturn(new ReattemptDecision(true, "worth another shot"));

        this.service.decideReattempt(ATTEMPT_ID);

        verify(this.taskGraphScheduler).dispatch(task, 2);
        verify(this.taskUpdateService).record(
            task, attempt, TaskUpdateType.UPDATE, "worth another shot",
            TaskUpdateAuthor.ORCHESTRATOR);
    }

    @Test
    void decideReattempt_whenStrategyDeclines_doesNotDispatch() {
        var task = new TaskEntity();
        task.setId("t1");
        task.setTitle("title");
        task.setDescription("description");
        var attempt = attemptOf(task, 1);
        when(this.taskAttemptRepository.findById(ATTEMPT_ID)).thenReturn(Optional.of(attempt));
        when(this.taskUpdateRepository.findFirstByTaskAttemptIdAndTypeInOrderByCreatedAtDesc(
                eq(ATTEMPT_ID), any()))
            .thenReturn(Optional.empty());
        when(this.taskUpdateRepository.findByTaskIdOrderByCreatedAtAsc("t1"))
            .thenReturn(List.of());
        when(this.reattemptDecisionStrategy.decide(any()))
            .thenReturn(new ReattemptDecision(false, "not worth it"));

        this.service.decideReattempt(ATTEMPT_ID);

        verify(this.taskGraphScheduler, never()).dispatch(any(), any(Integer.class));
        verify(this.taskUpdateService).record(
            task, attempt, TaskUpdateType.UPDATE, "not worth it", TaskUpdateAuthor.ORCHESTRATOR);
    }

    @Test
    void decideReattempt_includesTheLatestFailureReasoningInTheRequest() {
        var task = new TaskEntity();
        task.setId("t1");
        task.setTitle("title");
        task.setDescription("description");
        var attempt = attemptOf(task, 1);
        when(this.taskAttemptRepository.findById(ATTEMPT_ID)).thenReturn(Optional.of(attempt));
        var failureUpdate = new TaskUpdateEntity();
        failureUpdate.setType(TaskUpdateType.FAILED);
        failureUpdate.setDescription("did not converge");
        when(this.taskUpdateRepository.findFirstByTaskAttemptIdAndTypeInOrderByCreatedAtDesc(
                eq(ATTEMPT_ID), any()))
            .thenReturn(Optional.of(failureUpdate));
        when(this.taskUpdateRepository.findByTaskIdOrderByCreatedAtAsc("t1"))
            .thenReturn(List.of());
        when(this.reattemptDecisionStrategy.decide(any()))
            .thenReturn(new ReattemptDecision(false, "not worth it"));

        this.service.decideReattempt(ATTEMPT_ID);

        var requestCaptor = ArgumentCaptor.forClass(ReattemptDecisionRequest.class);
        verify(this.reattemptDecisionStrategy).decide(requestCaptor.capture());
        assertThat(requestCaptor.getValue().latestFailureReasoning()).isEqualTo(
            "did not converge");
        assertThat(requestCaptor.getValue().attemptNumber()).isEqualTo(1);
        assertThat(requestCaptor.getValue().maxAttempts()).isEqualTo(3);
    }

    @Test
    void decideReattempt_passesOnlyEarlierAttemptsUpdatesAsPriorUpdates() {
        var task = new TaskEntity();
        task.setId("t1");
        task.setTitle("title");
        task.setDescription("description");
        var earlierAttempt = attemptOf(task, 1);
        earlierAttempt.setId("earlier-attempt");
        var attempt = attemptOf(task, 2);
        when(this.taskAttemptRepository.findById(ATTEMPT_ID)).thenReturn(Optional.of(attempt));
        when(this.taskUpdateRepository.findFirstByTaskAttemptIdAndTypeInOrderByCreatedAtDesc(
                eq(ATTEMPT_ID), any()))
            .thenReturn(Optional.empty());
        when(this.taskUpdateRepository.findByTaskIdOrderByCreatedAtAsc("t1"))
            .thenReturn(List.of(
                updateOf(null, TaskUpdateType.CREATED, "task created"),
                updateOf(earlierAttempt, TaskUpdateType.FAILED, "earlier failure"),
                updateOf(attempt, TaskUpdateType.FAILED, "current failure")));
        when(this.reattemptDecisionStrategy.decide(any()))
            .thenReturn(new ReattemptDecision(false, "not worth it"));

        this.service.decideReattempt(ATTEMPT_ID);

        var requestCaptor = ArgumentCaptor.forClass(ReattemptDecisionRequest.class);
        verify(this.reattemptDecisionStrategy).decide(requestCaptor.capture());
        assertThat(requestCaptor.getValue().priorUpdateDescriptions())
            .containsExactly("Attempt 1 FAILED: earlier failure");
    }

    private static TaskUpdateEntity updateOf(
            final TaskAttemptEntity attempt, final TaskUpdateType type, final String description) {
        var update = new TaskUpdateEntity();
        update.setTaskAttempt(attempt);
        update.setType(type);
        update.setDescription(description);
        return update;
    }
}
