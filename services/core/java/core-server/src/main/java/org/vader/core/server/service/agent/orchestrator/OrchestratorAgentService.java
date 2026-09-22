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
import org.vader.common.model.vader.dto.TaskPlan;
import org.vader.common.model.vader.entity.TaskAttemptEntity;
import org.vader.common.model.vader.entity.TaskEntity;
import org.vader.common.model.vader.entity.TaskUpdateAuthor;
import org.vader.common.model.vader.entity.TaskUpdateEntity;
import org.vader.common.model.vader.entity.TaskUpdateType;
import org.vader.common.model.vader.entity.WorkflowEntity;
import org.vader.core.server.exceptions.OrchestratorResponseException;
import org.vader.core.server.models.ReattemptDecisionRequest;
import org.vader.core.server.models.WorkflowDecomposedEvent;
import org.vader.core.server.repository.ClientPromptRepository;
import org.vader.core.server.repository.TaskAttemptRepository;
import org.vader.core.server.repository.TaskUpdateRepository;
import org.vader.core.server.repository.WorkflowRepository;
import org.vader.core.server.service.agent.TaskUpdateService;
import org.vader.core.server.service.agent.orchestrator.strategies.interfaces.InterfaceLlmOrchestrationStrategy;
import org.vader.core.server.service.agent.orchestrator.strategies.interfaces.InterfaceReattemptDecisionStrategy;

/**
 * Turns an already-persisted client prompt into a persisted problem decomposition.
 *
 * <p>Called by {@code ClientPromptInbox} once a prompt has been popped from the queue. The prompt
 * (and any attached files) were stored by {@code ClientPromptIntakeService} when the request was
 * accepted, so this service only orchestrates and persists the workflow.</p>
 *
 * <p>The orchestrator LLM is asked to decompose the prompt; its response is parsed and validated
 * against the {@link TaskPlan} schema (jakarta bean validation) <em>before</em> a workflow is
 * written, so a malformed response leaves no workflow behind. On success the task plan, its task
 * graph and every task are persisted as a single graph hanging off a new {@link WorkflowEntity},
 * with the plan associated back to that workflow.</p>
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

    @Value("${vader.agent-harness.max-attempts-per-task:3}")
    private int maxAttemptsPerTask;

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
        var rawResponse = this.orchestrator.orchestrate(promptDto);
        var taskPlanDto = this.parseAndValidate(rawResponse);

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
            this.priorUpdateDescriptions(task.getId()));

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

    private List<String> priorUpdateDescriptions(final String taskId) {
        return this.taskUpdateRepository.findByTaskIdOrderByCreatedAtAsc(taskId).stream()
            .map(update -> update.getType() + ": " + update.getDescription())
            .toList();
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
