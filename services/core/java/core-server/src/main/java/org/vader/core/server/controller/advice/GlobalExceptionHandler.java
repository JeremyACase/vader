package org.vader.core.server.controller.advice;

import io.fabric8.kubernetes.client.KubernetesClientException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.vader.core.server.exceptions.AssignmentAlreadyTerminalException;
import org.vader.core.server.exceptions.OrchestratorUnavailableException;
import org.vader.core.server.exceptions.SandboxExecutionException;
import org.vader.core.server.exceptions.UnknownAssignmentException;
import org.vader.core.server.exceptions.UnknownToolException;
import org.vader.core.server.service.strategies.storage.FileStorageException;

/**
 * One place every REST exception-to-response mapping in {@code core-server} lives, following the
 * same {@code @ControllerAdvice}-per-service pattern the parallel Ubiquia project uses (see
 * {@code org.ubiquia.core.flow.controller.advice.GlobalExceptionHandler} and its shared
 * {@code AbstractGlobalExceptionHandler}) -- adapted here as one concrete class rather than an
 * abstract base plus a thin per-service subclass, since {@code core-server} is the only Spring
 * REST service in this project (Ubiquia's split exists because it has several).
 *
 * <p>Before this existed, each controller declared its own {@code @ExceptionHandler} methods,
 * three different error-body shapes were in use for the same concept (see {@link ErrorResponse}),
 * and any exception nobody had specifically thought to handle fell through to Spring Boot's bare
 * whitelabel JSON body -- indistinguishable, to a caller, from a genuine connectivity failure.
 * {@link #handleUnexpected} is the backstop that closes that gap for anything not explicitly
 * mapped below.</p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Maps an unknown assignment id to a 404.
     *
     * @param exception the lookup failure
     * @param request the originating request
     * @return a 404 response
     */
    @ExceptionHandler(UnknownAssignmentException.class)
    public ResponseEntity<ErrorResponse> handleUnknownAssignment(
        final UnknownAssignmentException exception, final HttpServletRequest request) {
        logger.warn("Rejected {}: {}", request.getRequestURI(), exception.getMessage());
        return status(HttpStatus.NOT_FOUND, "unknown_assignment", exception.getMessage());
    }

    /**
     * Maps a call against an already-settled assignment to a 409, so a stale or duplicate report
     * from a zombie harness cannot overwrite a newer outcome.
     *
     * @param exception the conflict
     * @param request the originating request
     * @return a 409 response
     */
    @ExceptionHandler(AssignmentAlreadyTerminalException.class)
    public ResponseEntity<ErrorResponse> handleAlreadyTerminal(
        final AssignmentAlreadyTerminalException exception, final HttpServletRequest request) {
        logger.warn("Rejected {}: {}", request.getRequestURI(), exception.getMessage());
        return status(HttpStatus.CONFLICT, "assignment_already_terminal", exception.getMessage());
    }

    /**
     * Maps an unreachable local LLM to a 502, since the failure originates upstream of this
     * service rather than in the caller's request.
     *
     * @param exception the upstream failure
     * @param request the originating request
     * @return a 502 response
     */
    @ExceptionHandler(OrchestratorUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleInferenceUnavailable(
        final OrchestratorUnavailableException exception, final HttpServletRequest request) {
        logger.warn("Inference gateway unavailable ({}): {}",
            request.getRequestURI(), exception.getMessage());
        return status(HttpStatus.BAD_GATEWAY, "inference_unavailable", exception.getMessage());
    }

    /**
     * Maps a tool-call request naming a tool that isn't registered to a 400 -- most likely a
     * model hallucinating a tool it was never actually offered.
     *
     * @param exception the lookup failure
     * @param request the originating request
     * @return a 400 response
     */
    @ExceptionHandler(UnknownToolException.class)
    public ResponseEntity<ErrorResponse> handleUnknownTool(
        final UnknownToolException exception, final HttpServletRequest request) {
        logger.warn("Rejected {}: {}", request.getRequestURI(), exception.getMessage());
        return status(HttpStatus.BAD_REQUEST, "unknown_tool", exception.getMessage());
    }

    /**
     * Maps a Kubernetes API failure to a 502, since the failure originates upstream of this
     * service rather than in the caller's request.
     *
     * @param exception the Kubernetes client failure
     * @param request the originating request
     * @return a 502 response
     */
    @ExceptionHandler(KubernetesClientException.class)
    public ResponseEntity<ErrorResponse> handleKubernetes(
        final KubernetesClientException exception, final HttpServletRequest request) {
        logger.warn("Kubernetes call failed ({}): {}",
            request.getRequestURI(), exception.getMessage());
        return status(HttpStatus.BAD_GATEWAY, "kubernetes_error", exception.getMessage());
    }

    /**
     * Maps a sandbox execution failure (unreachable, transport error) to a 502, mirroring how a
     * Kubernetes API failure above is treated -- upstream of this service, not the caller's fault.
     *
     * @param exception the execution failure
     * @param request the originating request
     * @return a 502 response
     */
    @ExceptionHandler(SandboxExecutionException.class)
    public ResponseEntity<ErrorResponse> handleExecutionFailure(
        final SandboxExecutionException exception, final HttpServletRequest request) {
        logger.warn("Sandbox execution failed ({}): {}",
            request.getRequestURI(), exception.getMessage());
        return status(HttpStatus.BAD_GATEWAY, "sandbox_execution_failed", exception.getMessage());
    }

    /**
     * Translates a file storage failure into a 500; the request reached the server but could not
     * be written to the backing store.
     *
     * @param exception the storage failure
     * @param request the originating request
     * @return a 500 response
     */
    @ExceptionHandler(FileStorageException.class)
    public ResponseEntity<ErrorResponse> handleFileStorage(
        final FileStorageException exception, final HttpServletRequest request) {
        logger.error("File storage failed ({}): {}",
            request.getRequestURI(), exception.getMessage(), exception);
        return status(
            HttpStatus.INTERNAL_SERVER_ERROR, "file_storage_failed", exception.getMessage());
    }

    /**
     * Translates a {@code @Valid}-triggered binding failure (e.g. too many attached files) into a
     * 400 listing every violated constraint.
     *
     * @param exception the binding failure
     * @param request the originating request
     * @return a 400 response
     */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<ErrorResponse> handleBindException(
        final BindException exception, final HttpServletRequest request) {
        var message = exception.getBindingResult().getAllErrors().stream()
            .map(ObjectError::getDefaultMessage)
            .collect(Collectors.joining("; "));
        logger.warn("Rejected {}: {}", request.getRequestURI(), message);
        return status(HttpStatus.BAD_REQUEST, "validation_failed", message);
    }

    /**
     * Translates a request body Jackson could not parse into a 400, rather than letting it fall
     * through to {@link #handleUnexpected} as an opaque 500.
     *
     * @param exception the parse failure
     * @param request the originating request
     * @return a 400 response
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleMalformedRequest(
        final HttpMessageNotReadableException exception, final HttpServletRequest request) {
        logger.warn("Rejected {}: malformed request body", request.getRequestURI());
        return status(HttpStatus.BAD_REQUEST, "malformed_request", exception.getMessage());
    }

    /**
     * Catches any other {@code IllegalArgumentException} -- e.g. an unknown back-pressure model
     * type -- as a 400. Deliberately generic: unlike every handler above, this exception type
     * is not specific to one failure, so the error code stays generic too rather than guessing at
     * which caller threw it.
     *
     * @param exception the invalid-argument failure
     * @param request the originating request
     * @return a 400 response
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(
        final IllegalArgumentException exception, final HttpServletRequest request) {
        logger.warn("Rejected {}: {}", request.getRequestURI(), exception.getMessage());
        return status(HttpStatus.BAD_REQUEST, "invalid_argument", exception.getMessage());
    }

    /**
     * Backstop for anything not specifically mapped above: still a well-formed
     * {@link ErrorResponse}, logged at {@code ERROR} with the full stack trace, rather than
     * Spring Boot's bare whitelabel body -- which a caller cannot distinguish from a genuine
     * connectivity failure (exactly the bug that motivated this class: an uncaught tool-call
     * exception surfacing to the harness as "tool-call invocation is unreachable").
     *
     * @param exception the unhandled failure
     * @param request the originating request
     * @return a 500 response
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(
        final Exception exception, final HttpServletRequest request) {
        logger.error("Unhandled exception on {}: {}",
            request.getRequestURI(), exception.getMessage(), exception);
        return status(HttpStatus.INTERNAL_SERVER_ERROR, "internal_error", exception.getMessage());
    }

    private static ResponseEntity<ErrorResponse> status(
            final HttpStatus status, final String error, final String message) {
        return ResponseEntity.status(status).body(new ErrorResponse(error, message));
    }
}
