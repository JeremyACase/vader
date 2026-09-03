package org.vader.core.server.controller;

import jakarta.validation.Valid;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.vader.common.library.implementation.service.mapper.ClientPromptDtoToEntityMapper;
import org.vader.common.library.implementation.service.mapper.WorkflowDtoMapper;
import org.vader.common.model.vader.dto.ClientPrompt;
import org.vader.common.model.vader.dto.Workflow;
import org.vader.core.server.orchestrator.OrchestratorResponseException;
import org.vader.core.server.orchestrator.OrchestratorUnavailableException;
import org.vader.core.server.service.WorkflowService;
import org.vader.core.server.storage.FileStorageException;

/**
 * Accepts client-submitted prompts and returns the problem decomposition the orchestrator LLM
 * produced for each one.
 */
@RestController
public class ClientPromptController {

    private static final Logger logger = LoggerFactory.getLogger(ClientPromptController.class);

    private final WorkflowService workflowService;
    private final ClientPromptDtoToEntityMapper clientPromptDtoToEntityMapper;
    private final WorkflowDtoMapper workflowDtoMapper;

    /**
     * Constructs the controller.
     *
     * @param workflowService decomposes a prompt into a persisted workflow
     * @param clientPromptDtoToEntityMapper maps the submitted prompt to an entity
     * @param workflowDtoMapper maps the persisted workflow back to a DTO for the response
     */
    public ClientPromptController(
        final WorkflowService workflowService,
        final ClientPromptDtoToEntityMapper clientPromptDtoToEntityMapper,
        final WorkflowDtoMapper workflowDtoMapper) {

        this.workflowService = workflowService;
        this.clientPromptDtoToEntityMapper = clientPromptDtoToEntityMapper;
        this.workflowDtoMapper = workflowDtoMapper;
    }

    /**
     * Decomposes an incoming client prompt and returns the resulting workflow.
     *
     * @param clientPrompt the submitted prompt text and any attached files
     * @return the workflow, including its task plan, spawned for the prompt
     */
    @PostMapping("/vader/core-server/client-prompt")
    @Transactional
    public ResponseEntity<Workflow> receivePrompt(
        @Valid @ModelAttribute final ClientPrompt clientPrompt) {

        logger.info(
            "Received client prompt: text='{}', fileCount={}",
            clientPrompt.getText(),
            clientPrompt.getFiles().size());

        var promptEntity = this.clientPromptDtoToEntityMapper.map(clientPrompt);
        var workflow = this.workflowService.decompose(promptEntity, clientPrompt.getFiles());
        return ResponseEntity.ok(this.workflowDtoMapper.map(workflow));
    }

    /**
     * Translates a constraint violation on the submitted prompt (e.g. too many attached files)
     * into a 400 so the caller knows to fix its request.
     *
     * @param exception the binding failure from {@code @Valid}
     * @return a 400 response listing each violated constraint
     */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<ErrorResponse> handleBindException(final BindException exception) {
        var message = exception.getBindingResult().getAllErrors().stream()
            .map(org.springframework.validation.ObjectError::getDefaultMessage)
            .collect(Collectors.joining("; "));
        logger.warn("Prompt rejected due to constraint violations: {}", message);
        return ResponseEntity.badRequest()
            .body(new ErrorResponse("validation_failed", message));
    }

    /**
     * Translates an unusable orchestrator response into a 502, since the failure originates
     * upstream of this service rather than in the client's request.
     *
     * @param exception the orchestrator failure
     * @return a 502 response describing the failure
     */
    @ExceptionHandler(OrchestratorResponseException.class)
    public ResponseEntity<ErrorResponse> handleOrchestratorResponse(
        final OrchestratorResponseException exception) {

        logger.warn("Orchestrator response was unusable: {}", exception.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
            .body(new ErrorResponse("orchestrator_response_invalid", exception.getMessage()));
    }

    /**
     * Translates an unreachable orchestrator into a 503, since the backend is likely still warming
     * up and the caller can retry.
     *
     * @param exception the transport failure
     * @return a 503 response describing the failure
     */
    @ExceptionHandler(OrchestratorUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleOrchestratorUnavailable(
        final OrchestratorUnavailableException exception) {

        logger.warn("Orchestrator is unavailable: {}", exception.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(new ErrorResponse("orchestrator_unavailable", exception.getMessage()));
    }

    /**
     * Translates a file storage failure into a 500; the upload reached the server but could not
     * be written to the backing store.
     *
     * @param exception the storage failure
     * @return a 500 response describing the failure
     */
    @ExceptionHandler(FileStorageException.class)
    public ResponseEntity<ErrorResponse> handleFileStorage(
        final FileStorageException exception) {

        logger.error("File storage failed: {}", exception.getMessage(), exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(new ErrorResponse("file_storage_failed", exception.getMessage()));
    }

    /**
     * Error body returned when a prompt cannot be decomposed.
     *
     * @param error a stable machine-readable code
     * @param message a human-readable description
     */
    public record ErrorResponse(String error, String message) {
    }
}
