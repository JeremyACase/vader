package org.vader.core.server.service.agent.orchestrator;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.vader.common.library.implementation.service.mapper.ClientPromptDtoMapper;
import org.vader.common.library.implementation.service.mapper.TaskPlanDtoToEntityMapper;
import org.vader.common.model.vader.dto.ClientPrompt;
import org.vader.common.model.vader.dto.TaskPlan;
import org.vader.common.model.vader.entity.TaskAttemptEntity;
import org.vader.common.model.vader.entity.TaskEntity;
import org.vader.common.model.vader.entity.TaskUpdateAuthor;
import org.vader.common.model.vader.entity.TaskUpdateEntity;
import org.vader.common.model.vader.entity.TaskUpdateType;
import org.vader.common.model.vader.entity.WorkflowEntity;
import org.vader.core.server.exceptions.OrchestratorResponseException;
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

/**
 * Turns an already-persisted client prompt into a persisted problem decomposition.
 *
 * <p>Called by {@code ClientPromptInbox} once a prompt has been popped from the queue. The prompt
 * (and any attached files) were stored by {@code ClientPromptIntakeService} when the request was
 * accepted, so this service only orchestrates and persists the workflow.</p>
 *
 * <p>The orchestrator LLM is asked to decompose the prompt; its response is parsed and validated
 * against the {@link TaskPlan} schema (jakarta bean validation) <em>before</em> a workflow is
 * written, so a malformed response leaves no workflow behind. Once schema-valid, the plan is
 * checked structurally ({@link TaskPlanStructuralValidator}) and critiqued
 * ({@link #taskPlanRefinementStrategy}) -- see {@link #decomposeWithRefinement} -- before it is
 * ever persisted. On success the task plan, its task graph and every task are persisted as a
 * single graph hanging off a new {@link WorkflowEntity}, with the plan associated back to that
 * workflow.</p>
 */
@Service
public class OrchestratorAgentService {

    private static final Logger logger = LoggerFactory.getLogger(OrchestratorAgentService.class);

    @Autowired
    private InterfaceLlmOrchestrationStrategy orchestrator;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private Validator validator;

    @Autowired
    private ClientPromptDtoMapper clientPromptDtoMapper;

    @Autowired
    private TaskPlanDtoToEntityMapper taskPlanDtoToEntityMapper;

    @Autowired
    private ClientPromptRepository clientPromptRepository;

    @Autowired
    private WorkflowRepository workflowRepository;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private TaskAttemptRepository taskAttemptRepository;

    @Autowired
    private TaskUpdateRepository taskUpdateRepository;

    @Autowired
    private TaskUpdateService taskUpdateService;

    @Autowired
    private InterfaceReattemptDecisionStrategy reattemptDecisionStrategy;

    @Autowired
    private TaskGraphScheduler taskGraphScheduler;

    @Autowired
    private InterfaceTaskPlanRefinementStrategy taskPlanRefinementStrategy;

    @Value("${vader.agent-harness.max-attempts-per-task:3}")
    private int maxAttemptsPerTask;

    @Value("${vader.orchestrator.max-task-plan-revisions:1}")
    private int maxTaskPlanRevisions;

    /**
     * Decomposes a persisted client prompt into a persisted task plan under a new workflow.
     *
     * @param clientPromptId the id of the persisted prompt to decompose
     * @return the persisted workflow, with its task plan attached
     * @throws OrchestratorResponseException if the orchestrator response is missing, unparseable,
     *     or fails the task-plan schema
     */
    @Transactional
    public WorkflowEntity decompose(final String clientPromptId) {

        var clientPrompt = this.clientPromptRepository.findById(clientPromptId).orElseThrow();
        var promptDto = this.clientPromptDtoMapper.map(clientPrompt);
        var taskPlanDto = this.decomposeWithRefinement(promptDto);

        var workflow = new WorkflowEntity();
        workflow.setClientPrompt(clientPrompt);

        var taskPlan = this.taskPlanDtoToEntityMapper.map(taskPlanDto);
        taskPlan.setWorkflow(workflow);
        workflow.setTaskPlan(taskPlan);

        var saved = this.workflowRepository.save(workflow);
        logger.info(
            "Persisted workflow {} with task plan {} ({} root tasks)",
            saved.getId(),
            saved.getTaskPlan().getId(),
            saved.getTaskPlan().getTaskGraph().getTasks().size());
        this.recordTaskCreatedUpdates(saved.getTaskPlan().getTaskGraph().getTasks());
        this.eventPublisher.publishEvent(new WorkflowDecomposedEvent(saved.getId()));
        return saved;
    }

    /**
     * Decomposes {@code promptDto}, then critiques the result -- structurally first (cheap,
     * deterministic, no LLM call), and via {@link #taskPlanRefinementStrategy} once that passes.
     * Dependencies the critique finds missing are added to the plan directly; any other problem
     * it finds is handled by re-decomposing with the critique fed back as guidance, for up to
     * {@link #maxTaskPlanRevisions} revisions. If the plan is still flagged once that budget is
     * spent, the last plan is used anyway (logged, not thrown) rather than leaving the prompt
     * stuck. This is not a canned fallback: the plan itself was produced successfully by the
     * LLM for this very request, it just still looks questionable -- unlike an unreachable LLM,
     * which always fails loudly rather than substituting anything.
     *
     * @param promptDto the original client prompt
     * @return the task plan to persist
     */
    private TaskPlan decomposeWithRefinement(final ClientPrompt promptDto) {
        var taskPlanDto = this.parseAndValidate(this.orchestrator.orchestrate(promptDto, null));
        var revision = 0;
        var problem = this.reviewAndPatch(promptDto, taskPlanDto);
        while (Objects.nonNull(problem) && revision < this.maxTaskPlanRevisions) {
            revision++;
            taskPlanDto = this.parseAndValidate(this.orchestrator.orchestrate(promptDto, problem));
            problem = this.reviewAndPatch(promptDto, taskPlanDto);
        }
        if (Objects.nonNull(problem)) {
            logger.warn(
                "TaskPlan refinement exhausted ({} revision(s)); proceeding with the last plan "
                    + "anyway: {}",
                this.maxTaskPlanRevisions, problem);
        }
        return taskPlanDto;
    }

    /**
     * Reviews a plan and returns the problem that still needs re-planning, if any -- patching
     * {@code taskPlanDto} in place with any dependencies the critique found missing along the
     * way.
     *
     * <p>The plan's structural problem, if it has one, comes first: there is no reason to spend
     * an LLM call finding a problem a deterministic check already found for free. Otherwise the
     * refinement strategy critiques it; missing dependencies it names are added directly (see
     * {@link TaskPlanDependencyPatcher} for why re-planning can't be trusted to add them), and
     * only a problem that needs the plan redone is returned.</p>
     */
    private String reviewAndPatch(final ClientPrompt promptDto, final TaskPlan taskPlanDto) {
        var structural = TaskPlanStructuralValidator.validate(taskPlanDto);
        String problem;
        if (!structural.valid()) {
            problem = structural.problem();
        } else {
            var verdict = this.taskPlanRefinementStrategy.critique(
                new TaskPlanRefinementRequest(promptDto.getText(), taskPlanDto));
            this.addMissingDependencies(taskPlanDto, verdict);
            problem = verdict.needsRevision() ? verdict.reasoning() : null;
        }
        return problem;
    }

    private void addMissingDependencies(
            final TaskPlan taskPlanDto, final TaskPlanRefinementVerdict verdict) {
        var added = TaskPlanDependencyPatcher.apply(taskPlanDto, verdict.missingDependencies());
        if (!added.isEmpty()) {
            logger.info("Added {} dependency(ies) the plan critique found missing: {}",
                added.size(), added);
        }
    }


    /**
     * Records a {@link TaskUpdateType#CREATED} update for every task in the graph, including
     * nested subtasks, so a task's history starts the moment it exists rather than only ever
     * accumulating entries once something happens to it.
     *
     * @param rootTasks the task graph's root tasks
     */
    private void recordTaskCreatedUpdates(final Set<TaskEntity> rootTasks) {
        this.flattenTasks(rootTasks).forEach(task ->
            this.taskUpdateService.record(
                task, null, TaskUpdateType.CREATED, task.getDescription(),
                TaskUpdateAuthor.SYSTEM));
    }

    private List<TaskEntity> flattenTasks(final Set<TaskEntity> tasks) {
        var flat = new ArrayList<TaskEntity>();
        tasks.forEach(task -> {
            flat.add(task);
            flat.addAll(this.flattenTasks(task.getSubTasks()));
        });
        return flat;
    }

    /**
     * Decides whether a failed attempt is worth re-attempting, and dispatches a fresh attempt if
     * so. Called only from {@code TaskAttemptReviewService}, after an evaluator (or a
     * deterministic timeout/stall verdict) has already judged the attempt a failure.
     *
     * <p>The attempt-count cap is a hard ceiling checked here before any LLM call: once reached,
     * this deterministically gives up rather than asking the strategy at all.</p>
     *
     * @param attemptId the failed attempt's id
     */
    @Transactional
    public void decideReattempt(final String attemptId) {
        var attempt = this.taskAttemptRepository.findById(attemptId).orElseThrow();
        var task = attempt.getTask();

        if (attempt.getAttemptNumber() >= this.maxAttemptsPerTask) {
            this.taskUpdateService.record(task, attempt, TaskUpdateType.UPDATE,
                "Attempt cap (" + this.maxAttemptsPerTask + ") reached; not re-attempting.",
                TaskUpdateAuthor.ORCHESTRATOR);
            return;
        }

        var latestFailureReasoning = this.latestFailureReasoning(attempt);
        var request = new ReattemptDecisionRequest(
            task.getTitle(), task.getDescription(), attempt.getAttemptNumber(),
            this.maxAttemptsPerTask, latestFailureReasoning,
            this.priorAttemptUpdateDescriptions(task.getId(), attempt.getId()));

        var decision = this.reattemptDecisionStrategy.decide(request);
        this.taskUpdateService.record(task, attempt, TaskUpdateType.UPDATE, decision.reasoning(),
            TaskUpdateAuthor.ORCHESTRATOR);
        if (decision.shouldReattempt()) {
            this.taskGraphScheduler.dispatch(task, attempt.getAttemptNumber() + 1);
        }
    }

    private String latestFailureReasoning(final TaskAttemptEntity attempt) {
        var verdictTypes = List.of(TaskUpdateType.FAILED, TaskUpdateType.TIMED_OUT);
        return this.taskUpdateRepository
            .findFirstByTaskAttemptIdAndTypeInOrderByCreatedAtDesc(attempt.getId(), verdictTypes)
            .map(TaskUpdateEntity::getDescription)
            .orElse("(no failure reasoning recorded)");
    }

    /**
     * Describes only the updates recorded against <em>earlier</em> attempts of this task. The
     * attempt being judged is excluded -- its failure verdict already goes in as
     * {@code latestFailureReasoning} -- and so are task-level updates tied to no attempt at all.
     * Including the current attempt's own verdict here showed a small model the same failure
     * twice, which it read as "the same failure repeating" and declined to retry on attempt 1.
     */
    private List<String> priorAttemptUpdateDescriptions(
            final String taskId, final String currentAttemptId) {
        return this.taskUpdateRepository.findByTaskIdOrderByCreatedAtAsc(taskId).stream()
            .filter(update -> isFromAnEarlierAttempt(update, currentAttemptId))
            .map(update -> "Attempt " + update.getTaskAttempt().getAttemptNumber() + " "
                + update.getType() + ": " + update.getDescription())
            .toList();
    }

    private static boolean isFromAnEarlierAttempt(
            final TaskUpdateEntity update, final String currentAttemptId) {
        return Objects.nonNull(update.getTaskAttempt())
            && !currentAttemptId.equals(update.getTaskAttempt().getId());
    }

    private TaskPlan parseAndValidate(final String rawResponse) {

        if (Objects.isNull(rawResponse) || rawResponse.isBlank()) {
            throw new OrchestratorResponseException("Orchestrator returned an empty response.");
        }

        final TaskPlan taskPlan;
        try {
            taskPlan = this.objectMapper.readValue(rawResponse, TaskPlan.class);
        } catch (JsonProcessingException e) {
            throw new OrchestratorResponseException(
                "Orchestrator response could not be parsed as a task plan.", e);
        }

        var violations = this.validator.validate(taskPlan);
        if (!violations.isEmpty()) {
            throw new OrchestratorResponseException(
                "Orchestrator response did not satisfy the task-plan schema: "
                    + this.describe(violations));
        }
        return taskPlan;
    }

    private String describe(final Set<ConstraintViolation<TaskPlan>> violations) {
        return violations.stream()
            .map(violation -> violation.getPropertyPath() + " " + violation.getMessage())
            .sorted()
            .collect(Collectors.joining(", "));
    }
}
