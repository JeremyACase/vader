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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.vader.common.library.implementation.service.mapper.ClientPromptDtoMapper;
import org.vader.common.library.implementation.service.mapper.TaskPlanDtoToEntityMapper;
import org.vader.common.model.vader.dto.TaskPlan;
import org.vader.common.model.vader.entity.ClientPromptEntity;
import org.vader.common.model.vader.entity.WorkflowEntity;
import org.vader.core.server.orchestrator.OrchestratorResponseException;
import org.vader.core.server.orchestrator.interfaces.InterfaceLlmOrchestrationStrategy;
import org.vader.core.server.repository.ClientPromptRepository;
import org.vader.core.server.repository.WorkflowRepository;

/**
 * Turns a client prompt into a persisted problem decomposition.
 *
 * <p>The orchestrator LLM is asked to decompose the prompt; its response is parsed and validated
 * against the {@link TaskPlan} schema (jakarta bean validation) <em>before</em> anything is
 * written, so a malformed response leaves the database untouched. On success the task plan, its
 * task graph and every task are persisted as a single graph hanging off a new
 * {@link WorkflowEntity}, with the plan associated back to that workflow.</p>
 */
@Service
public class WorkflowService {

    private static final Logger logger = LoggerFactory.getLogger(WorkflowService.class);

    private final InterfaceLlmOrchestrationStrategy orchestrator;
    private final ObjectMapper objectMapper;
    private final Validator validator;
    private final ClientPromptDtoMapper clientPromptDtoMapper;
    private final TaskPlanDtoToEntityMapper taskPlanDtoToEntityMapper;
    private final ClientPromptRepository clientPromptRepository;
    private final WorkflowRepository workflowRepository;

    /**
     * Constructs the service.
     *
     * @param orchestrator the orchestrator strategy to ask for a decomposition
     * @param objectMapper the JSON mapper used to parse the orchestrator response
     * @param validator the bean validator used to enforce the task-plan schema
     * @param clientPromptDtoMapper maps the prompt entity to the DTO the orchestrator expects
     * @param taskPlanDtoToEntityMapper maps the validated task plan to a persistable entity graph
     * @param clientPromptRepository repository for the originating client prompt
     * @param workflowRepository repository for the spawned workflow
     */
    public WorkflowService(
        final InterfaceLlmOrchestrationStrategy orchestrator,
        final ObjectMapper objectMapper,
        final Validator validator,
        final ClientPromptDtoMapper clientPromptDtoMapper,
        final TaskPlanDtoToEntityMapper taskPlanDtoToEntityMapper,
        final ClientPromptRepository clientPromptRepository,
        final WorkflowRepository workflowRepository) {

        this.orchestrator = orchestrator;
        this.objectMapper = objectMapper;
        this.validator = validator;
        this.clientPromptDtoMapper = clientPromptDtoMapper;
        this.taskPlanDtoToEntityMapper = taskPlanDtoToEntityMapper;
        this.clientPromptRepository = clientPromptRepository;
        this.workflowRepository = workflowRepository;
    }

    /**
     * Decomposes a client prompt into a persisted task plan under a new workflow.
     *
     * @param clientPrompt the prompt to decompose
     * @return the persisted workflow, with its task plan attached
     * @throws OrchestratorResponseException if the orchestrator response is missing, unparseable,
     *     or fails the task-plan schema
     */
    @Transactional
    public WorkflowEntity decompose(final ClientPromptEntity clientPrompt) {

        var promptDto = this.clientPromptDtoMapper.map(clientPrompt);
        var rawResponse = this.orchestrator.orchestrate(promptDto);
        var taskPlanDto = this.parseAndValidate(rawResponse);

        var persistedPrompt = this.clientPromptRepository.save(clientPrompt);

        var workflow = new WorkflowEntity();
        workflow.setClientPrompt(persistedPrompt);

        var taskPlan = this.taskPlanDtoToEntityMapper.map(taskPlanDto);
        taskPlan.setWorkflow(workflow);
        workflow.setTaskPlan(taskPlan);

        var saved = this.workflowRepository.save(workflow);
        logger.info(
            "Persisted workflow {} with task plan {} ({} root tasks)",
            saved.getId(),
            saved.getTaskPlan().getId(),
            saved.getTaskPlan().getTaskGraph().getTasks().size());
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
