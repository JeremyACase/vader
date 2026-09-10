package org.vader.core.server.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.vader.common.library.implementation.service.mapper.ClientPromptDtoMapper;
import org.vader.common.library.implementation.service.mapper.TaskPlanDtoToEntityMapper;
import org.vader.common.model.vader.dto.TaskPlan;
import org.vader.common.model.vader.entity.ClientPromptEntity;
import org.vader.common.model.vader.entity.WorkflowEntity;
import org.vader.core.exceptions.OrchestratorResponseException;
import org.vader.core.server.orchestrator.interfaces.InterfaceLlmOrchestrationStrategy;
import org.vader.core.server.repository.ClientPromptRepository;
import org.vader.core.server.repository.WorkflowRepository;
import org.vader.core.server.storage.interfaces.InterfaceFileStorageStrategy;

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

    @Autowired
    private InterfaceLlmOrchestrationStrategy orchestrator;

    @Autowired
    private InterfaceFileStorageStrategy fileStorageStrategy;

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

    /**
     * Decomposes a client prompt into a persisted task plan under a new workflow.
     *
     * <p>Attached files are stored via the active {@link InterfaceFileStorageStrategy} before the
     * prompt entity is saved, so the JPA cascade persists both the metadata and the content
     * (or the MinIO reference) in the same transaction.</p>
     *
     * @param clientPrompt the prompt to decompose
     * @param files any files attached to the prompt; may be empty
     * @return the persisted workflow, with its task plan attached
     * @throws OrchestratorResponseException if the orchestrator response is missing, unparseable,
     *     or fails the task-plan schema
     */
    @Transactional
    public WorkflowEntity decompose(
        final ClientPromptEntity clientPrompt,
        final List<MultipartFile> files) {

        var promptDto = this.clientPromptDtoMapper.map(clientPrompt);
        var rawResponse = this.orchestrator.orchestrate(promptDto);
        var taskPlanDto = this.parseAndValidate(rawResponse);

        if (!files.isEmpty()) {
            var storedFiles = this.fileStorageStrategy.store(files);
            storedFiles.forEach(f -> f.setClientPrompt(clientPrompt));
            clientPrompt.setFiles(new LinkedHashSet<>(storedFiles));
        }

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
