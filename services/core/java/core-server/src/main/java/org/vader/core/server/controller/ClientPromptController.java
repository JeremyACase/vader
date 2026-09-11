package org.vader.core.server.controller;

import jakarta.validation.Valid;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.vader.common.model.vader.IngressResponse;
import org.vader.common.model.vader.dto.ClientPrompt;
import org.vader.core.server.service.ClientPromptIntakeService;
import org.vader.core.server.service.strategies.storage.FileStorageException;

/**
 * Accepts client-submitted prompts. The prompt is persisted and queued for decomposition; the
 * response is a {@link IngressResponse} receipt, not the finished workflow. Callers poll the
 * workflow query API by {@code clientPromptId} for the eventual decomposition, and the
 * backpressure endpoint for how backed up the queue is.
 */
@RestController
public class ClientPromptController {

    private static final Logger logger = LoggerFactory.getLogger(ClientPromptController.class);

    @Autowired
    private ClientPromptIntakeService clientPromptIntakeService;

    /**
     * Accepts a client prompt for asynchronous decomposition.
     *
     * @param clientPrompt the submitted prompt text and any attached files
     * @return 202 with a receipt identifying the persisted prompt
     */
    @PostMapping("/vader/core-server/client-prompt")
    public ResponseEntity<IngressResponse> receivePrompt(
        @Valid @ModelAttribute final ClientPrompt clientPrompt) {

        logger.info(
            "Received client prompt: text='{}', fileCount={}",
            clientPrompt.getText(),
            clientPrompt.getFiles().size());

        var ingressResponse = this.clientPromptIntakeService.accept(clientPrompt);
        return ResponseEntity.accepted().body(ingressResponse);
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
     * Error body returned when a prompt cannot be accepted.
     *
     * @param error a stable machine-readable code
     * @param message a human-readable description
     */
    public record ErrorResponse(String error, String message) {
    }
}
