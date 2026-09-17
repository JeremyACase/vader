package org.vader.core.server.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.vader.common.library.implementation.service.mapper.ClientPromptDtoMapper;
import org.vader.common.library.implementation.service.mapper.TaskPlanDtoToEntityMapper;
import org.vader.common.model.vader.dto.TaskPlan;
import org.vader.common.model.vader.entity.WorkflowEntity;
import org.vader.core.server.exceptions.OrchestratorResponseException;
import org.vader.core.server.models.WorkflowDecomposedEvent;
import org.vader.core.server.repository.ClientPromptRepository;
import org.vader.core.server.repository.WorkflowRepository;
import org.vader.core.server.service.strategies.orchestration.interfaces.InterfaceLlmOrchestrationStrategy;

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
public class WorkflowService {

    private static final Logger logger = LoggerFactory.getLogger(WorkflowService.class);

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
        this.eventPublisher.publishEvent(new WorkflowDecomposedEvent(saved.getId()));
        return saved;
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
